package eu.appbahn.operator.reconciler.imagesource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.operator.reconciler.imagesource.buildjob.BuildJobDependentResource;
import eu.appbahn.operator.reconciler.imagesource.buildjob.BuildJobReconcileCondition;
import eu.appbahn.operator.reconciler.imagesource.buildjob.BuildOrchestrator;
import eu.appbahn.operator.tunnel.OperatorEventPublisher;
import eu.appbahn.operator.tunnel.OperatorTunnelConfig;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.imagesource.DownstreamReference;
import eu.appbahn.shared.crd.imagesource.ImageSourceCondition;
import eu.appbahn.shared.crd.imagesource.ImageSourceConditions;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourcePromotionSpec;
import eu.appbahn.shared.crd.imagesource.ImageSourceSpec;
import eu.appbahn.shared.crd.imagesource.ImageSourceStatus;
import eu.appbahn.shared.crd.imagesource.ImageSourceType;
import eu.appbahn.shared.crd.imagesource.ImageSourceUpstreamSpec;
import eu.appbahn.shared.crd.imagesource.LatestArtifact;
import eu.appbahn.shared.crd.imagesource.PendingBuild;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reconciles {@link ImageSourceCrd}. For {@code type: git} the reconciler polls the upstream
 * branch on {@code spec.trigger.poll.intervalSeconds} cadence, writes the HEAD SHA into
 * {@code status.observedCommit}, and advances the build state machine via {@link
 * BuildOrchestrator}. For {@code type: image} it mirrors {@code spec.image.ref} directly into
 * {@code status.latestArtifact} on every reconcile.
 *
 * <p>Build {@link Job} CRUD is handled by the JOSDK workflow — see {@link
 * BuildJobDependentResource}. The orchestrator only mutates the {@link
 * ImageSourceStatus#getPendingBuild() pendingBuild} slot; the dependent reads it and
 * materializes the K8s Job. {@code explicitInvocation = true} so the workflow runs after
 * the orchestrator has stamped the slot with a {@code jobName}.
 */
@Component
@ControllerConfiguration
@Workflow(
        explicitInvocation = true,
        dependents = {
            @Dependent(type = BuildJobDependentResource.class, reconcilePrecondition = BuildJobReconcileCondition.class)
        })
public class ImageSourceReconciler implements Reconciler<ImageSourceCrd>, Cleaner<ImageSourceCrd> {

    private static final Logger log = LoggerFactory.getLogger(ImageSourceReconciler.class);

    static final int DEFAULT_INTERVAL_SECONDS = 60;
    static final int DEFAULT_INTERVAL_AFTER_WEBHOOK_SECONDS = 3600;
    static final int DEFAULT_WEBHOOK_FRESHNESS_SECONDS = 86400;
    static final int IMAGE_TYPE_RESCHEDULE_SECONDS = 300;
    static final int IMAGE_SOURCE_TYPE_RESCHEDULE_SECONDS = 30;

    private final GitClient gitClient;
    private final OperatorEventPublisher eventPublisher;
    private final BuildOrchestrator buildOrchestrator;
    private final ObjectMapper objectMapper;
    private final String ownClusterName;

    public ImageSourceReconciler(
            GitClient gitClient,
            OperatorEventPublisher eventPublisher,
            BuildOrchestrator buildOrchestrator,
            OperatorTunnelConfig tunnelConfig,
            ObjectMapper objectMapper) {
        this.gitClient = gitClient;
        this.eventPublisher = eventPublisher;
        this.buildOrchestrator = buildOrchestrator;
        this.objectMapper = objectMapper;
        this.ownClusterName = tunnelConfig.clusterName();
    }

    /**
     * Watch sibling Resources so an arriving Resource triggers the OwnerReference auto-bind
     * without waiting for the slow re-reconcile schedule. Binding is by same-name same-namespace
     * convention — a Resource arriving in namespace N with name X re-reconciles ImageSource N/X.
     * Also watch upstream ImageSources so a downstream {@code type: imageSource} promotion
     * reconciles promptly when the upstream's {@code latestArtifact} changes. Build Jobs are
     * watched via the {@code @Workflow} dependent (see {@link BuildJobDependentResource}).
     */
    @Override
    public List<EventSource<?, ImageSourceCrd>> prepareEventSources(EventSourceContext<ImageSourceCrd> context) {
        var resourceInformerConfig = InformerEventSourceConfiguration.from(ResourceCrd.class, ImageSourceCrd.class)
                .withSecondaryToPrimaryMapper(resource -> {
                    if (resource.getMetadata() == null) {
                        return Set.of();
                    }
                    String name = resource.getMetadata().getName();
                    String namespace = resource.getMetadata().getNamespace();
                    if (name == null || namespace == null) {
                        return Set.of();
                    }
                    return Set.of(new ResourceID(name, namespace));
                })
                .build();
        // Upstream ImageSource → all downstream ImageSources whose spec.imageSource.upstream
        // points at it (cluster matches own; namespace+name match the upstream CR). When the
        // changed CR is itself a downstream (type: imageSource), also re-trigger the upstream
        // it points at — needed so the upstream's cleanup() sees the downstream's deletion and
        // can drop the upstream-protection finalizer.
        var upstreamInformerConfig = InformerEventSourceConfiguration.from(ImageSourceCrd.class, ImageSourceCrd.class)
                .withSecondaryToPrimaryMapper(changed -> {
                    if (changed.getMetadata() == null) return Set.of();
                    String changedName = changed.getMetadata().getName();
                    String changedNs = changed.getMetadata().getNamespace();
                    var hits = new java.util.HashSet<ResourceID>();

                    // Forward: this CR (an upstream) changed → re-reconcile downstreams.
                    var allImageSources = context.getClient()
                            .resources(ImageSourceCrd.class)
                            .inAnyNamespace()
                            .list()
                            .getItems();
                    for (var ds : allImageSources) {
                        if (ds.getSpec() == null
                                || ds.getSpec().getType() != ImageSourceType.IMAGE_SOURCE
                                || ds.getSpec().getImageSource() == null
                                || ds.getSpec().getImageSource().getUpstream() == null) {
                            continue;
                        }
                        var ups = ds.getSpec().getImageSource().getUpstream();
                        boolean clusterOk = ups.getCluster() == null
                                || ups.getCluster().isBlank()
                                || ups.getCluster().equals(ownClusterName);
                        if (clusterOk && changedName.equals(ups.getName()) && changedNs.equals(ups.getNamespace())) {
                            hits.add(new ResourceID(
                                    ds.getMetadata().getName(), ds.getMetadata().getNamespace()));
                        }
                    }

                    // Reverse: this CR is a downstream → re-reconcile its upstream so upstream
                    // cleanup observes the (potentially deleted) downstream.
                    if (changed.getSpec() != null
                            && changed.getSpec().getType() == ImageSourceType.IMAGE_SOURCE
                            && changed.getSpec().getImageSource() != null
                            && changed.getSpec().getImageSource().getUpstream() != null) {
                        var ups = changed.getSpec().getImageSource().getUpstream();
                        boolean clusterOk = ups.getCluster() == null
                                || ups.getCluster().isBlank()
                                || ups.getCluster().equals(ownClusterName);
                        if (clusterOk && ups.getName() != null && ups.getNamespace() != null) {
                            hits.add(new ResourceID(ups.getName(), ups.getNamespace()));
                        }
                    }

                    return hits;
                })
                .build();
        return List.of(
                new InformerEventSource<>(resourceInformerConfig, context),
                new InformerEventSource<>(upstreamInformerConfig, context));
    }

    @Override
    public UpdateControl<ImageSourceCrd> reconcile(ImageSourceCrd cr, Context<ImageSourceCrd> context) {
        String name = cr.getMetadata().getName();
        String namespace = cr.getMetadata().getNamespace();
        log.info("Reconciling ImageSource {}/{}", namespace, name);

        if (cr.getSpec() == null) {
            log.debug("Reconcile called with null spec for {}; skipping", name);
            return UpdateControl.noUpdate();
        }

        ImageSourceSpec spec = cr.getSpec();
        ImageSourceStatus prev = cr.getStatus();
        ImageSourceStatus next = new ImageSourceStatus();
        next.setObservedGeneration(cr.getMetadata().getGeneration());
        if (prev != null) {
            next.setObservedCommit(prev.getObservedCommit());
            next.setLatestArtifact(prev.getLatestArtifact());
            next.setLastWebhookAt(prev.getLastWebhookAt());
            next.setPendingBuild(prev.getPendingBuild());
            next.setQueuedBuild(prev.getQueuedBuild());
        }

        Optional<String> validationError = validate(spec);
        if (validationError.isPresent()) {
            applyConfigInvalid(next, validationError.get());
            return finalize(cr, prev, next);
        }

        // Stamp the upstream-protection finalizer on every reconcile so cleanup() runs even
        // if the CR was created before this controller version. Cleanup removes it once no
        // downstreams remain.
        ensureUpstreamProtectionFinalizer(cr, context);

        // Auto-bind OwnerReference on the kubectl-apply path. The platform-driven path sets
        // the OwnerReference inline in ApplyResourceBundle; on this path we look up which
        // Resource (if exactly one) refers to this ImageSource and stamp the reference.
        ensureOwnerReference(cr, context, next);

        return switch (spec.getType()) {
            case GIT -> reconcileGit(cr, spec, prev, next, namespace, context);
            case IMAGE -> reconcileImage(cr, spec, prev, next);
            case IMAGE_SOURCE -> reconcileImageSource(cr, spec, prev, next, context);
        };
    }

    /**
     * Set the ImageSource's controller {@link OwnerReference} to the sibling Resource with the
     * same name in the same namespace (1:1 binding by convention). If no such Resource exists yet,
     * surface {@code OwnerNotResolved/NoMatch} and leave ownerReferences untouched — the
     * Resource→ImageSource informer will re-trigger this reconcile when the Resource arrives.
     */
    private void ensureOwnerReference(ImageSourceCrd cr, Context<ImageSourceCrd> context, ImageSourceStatus next) {
        String namespace = cr.getMetadata().getNamespace();
        String name = cr.getMetadata().getName();
        ResourceCrd owner;
        try {
            owner = context.getClient()
                    .resources(ResourceCrd.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
        } catch (Exception e) {
            log.debug("OwnerReference lookup failed for {}/{}: {}", namespace, name, e.getMessage());
            return;
        }
        if (owner == null) {
            upsertCondition(
                    next,
                    ImageSourceConditions.TYPE_OWNER_NOT_RESOLVED,
                    ImageSourceConditions.STATUS_TRUE,
                    ImageSourceConditions.REASON_NO_MATCH,
                    "No Resource named " + name + " in namespace " + namespace);
            return;
        }
        String uid = owner.getMetadata() != null ? owner.getMetadata().getUid() : null;
        if (uid == null || uid.isBlank()) {
            log.debug("Owner Resource {}/{} has no UID yet; deferring owner-bind", namespace, name);
            return;
        }

        if (controllerOwnerMatches(cr, owner)) {
            removeCondition(next, ImageSourceConditions.TYPE_OWNER_NOT_RESOLVED);
            return;
        }

        OwnerReference ref = new OwnerReferenceBuilder()
                .withApiVersion(owner.getApiVersion())
                .withKind(owner.getKind())
                .withName(owner.getMetadata().getName())
                .withUid(uid)
                .withController(true)
                .withBlockOwnerDeletion(true)
                .build();
        cr.getMetadata().setOwnerReferences(java.util.List.of(ref));
        // Patch the metadata immediately so the owner-bind sticks even if the rest of the
        // reconcile aborts. Status is updated via finalize() at the end of the reconcile.
        try {
            context.getClient()
                    .resources(ImageSourceCrd.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .edit(existing -> {
                        existing.getMetadata().setOwnerReferences(java.util.List.of(ref));
                        return existing;
                    });
        } catch (Exception e) {
            log.warn("Failed to persist OwnerReference for ImageSource {}/{}: {}", namespace, name, e.getMessage());
        }
        removeCondition(next, ImageSourceConditions.TYPE_OWNER_NOT_RESOLVED);
    }

    /**
     * Returns true when the CR's existing controller {@link OwnerReference} already points at the
     * given Resource (matching apiVersion + kind + name). Avoids spurious patches when nothing
     * needs to change.
     */
    private static boolean controllerOwnerMatches(ImageSourceCrd cr, ResourceCrd owner) {
        if (cr.getMetadata() == null || cr.getMetadata().getOwnerReferences() == null) {
            return false;
        }
        return cr.getMetadata().getOwnerReferences().stream()
                .filter(o -> Boolean.TRUE.equals(o.getController()))
                .anyMatch(o -> Objects.equals(o.getApiVersion(), owner.getApiVersion())
                        && Objects.equals(o.getKind(), owner.getKind())
                        && Objects.equals(
                                o.getName(),
                                owner.getMetadata() != null
                                        ? owner.getMetadata().getName()
                                        : null));
    }

    /**
     * Two-layer deletion protection:
     *
     * <ol>
     *   <li>Same-cluster: list every {@link ImageSourceCrd} cluster-wide and look for a
     *       {@code type: imageSource} entry whose {@code spec.imageSource.upstream} points back at
     *       this CR. Any match blocks deletion.
     *   <li>Cross-cluster: the platform writes
     *       {@link Labels#ANNOTATION_DOWNSTREAM_REFERENCES} (a JSON array of
     *       {@code {cluster, namespace, name}}) on this CR via {@code ApplyResourceBundle} on
     *       every sync batch. A non-empty array blocks deletion.
     * </ol>
     *
     * When blocked, returns {@link DeleteControl#noFinalizerRemoval()} after re-patching the
     * status with a {@code BlockedByDownstream} condition; the deletion request stays pending
     * until all downstreams are gone.
     */
    @Override
    public DeleteControl cleanup(ImageSourceCrd cr, Context<ImageSourceCrd> context) {
        String name = cr.getMetadata().getName();
        String namespace = cr.getMetadata().getNamespace();

        List<DownstreamReference> blockers = collectBlockers(cr, context);
        if (!blockers.isEmpty()) {
            log.info("Blocking deletion of ImageSource {}/{}: {} downstream(s)", namespace, name, blockers.size());
            try {
                ImageSourceStatus status = cr.getStatus() != null ? cr.getStatus() : new ImageSourceStatus();
                upsertCondition(
                        status,
                        ImageSourceConditions.TYPE_BLOCKED_BY_DOWNSTREAM,
                        ImageSourceConditions.STATUS_TRUE,
                        ImageSourceConditions.REASON_DOWNSTREAMS_EXIST,
                        formatBlockerMessage(blockers));
                cr.setStatus(status);
                context.getClient()
                        .resources(ImageSourceCrd.class)
                        .inNamespace(namespace)
                        .withName(name)
                        .patchStatus(cr);
            } catch (Exception e) {
                log.warn("Failed to patch BlockedByDownstream status on {}/{}: {}", namespace, name, e.getMessage());
            }
            return DeleteControl.noFinalizerRemoval();
        }

        // Drop our own finalizer; JOSDK only removes its configured finalizer, so without this
        // step the CR would stay around even after cleanup() OK'd deletion.
        removeUpstreamProtectionFinalizer(cr, context);

        try {
            eventPublisher.emitImageSourceDeleted(name);
            log.info("Notified platform of ImageSource deletion: {}", name);
        } catch (Exception e) {
            log.warn("Failed to notify platform of ImageSource deletion {}: {}", name, e.getMessage());
        }
        return DeleteControl.defaultDelete();
    }

    private void removeUpstreamProtectionFinalizer(ImageSourceCrd cr, Context<ImageSourceCrd> context) {
        if (cr.getMetadata() == null || cr.getMetadata().getFinalizers() == null) return;
        if (!cr.getMetadata().getFinalizers().contains(Labels.FINALIZER_UPSTREAM_PROTECTION)) return;
        try {
            context.getClient()
                    .resources(ImageSourceCrd.class)
                    .inNamespace(cr.getMetadata().getNamespace())
                    .withName(cr.getMetadata().getName())
                    .edit(existing -> {
                        var meta = existing.getMetadata();
                        if (meta.getFinalizers() != null) {
                            var fin = new ArrayList<>(meta.getFinalizers());
                            fin.remove(Labels.FINALIZER_UPSTREAM_PROTECTION);
                            meta.setFinalizers(fin);
                        }
                        return existing;
                    });
        } catch (Exception e) {
            log.warn(
                    "Failed to remove {} finalizer on {}/{}: {}",
                    Labels.FINALIZER_UPSTREAM_PROTECTION,
                    cr.getMetadata().getNamespace(),
                    cr.getMetadata().getName(),
                    e.getMessage());
        }
    }

    /**
     * Walk every ImageSource in the cluster looking for {@code type: imageSource} downstreams
     * that target this CR (cluster matches own or is blank, and namespace+name match). Then
     * decode the cross-cluster annotation. Returns a deduplicated, sorted list of references.
     */
    private List<DownstreamReference> collectBlockers(ImageSourceCrd cr, Context<ImageSourceCrd> context) {
        var blockers = new ArrayList<DownstreamReference>();
        String selfName = cr.getMetadata().getName();
        String selfNamespace = cr.getMetadata().getNamespace();
        try {
            var all = context.getClient()
                    .resources(ImageSourceCrd.class)
                    .inAnyNamespace()
                    .list()
                    .getItems();
            for (var ds : all) {
                if (ds.getMetadata() == null) continue;
                if (selfName.equals(ds.getMetadata().getName())
                        && selfNamespace.equals(ds.getMetadata().getNamespace())) {
                    continue; // skip self
                }
                if (ds.getSpec() == null
                        || ds.getSpec().getType() != ImageSourceType.IMAGE_SOURCE
                        || ds.getSpec().getImageSource() == null
                        || ds.getSpec().getImageSource().getUpstream() == null) {
                    continue;
                }
                var ups = ds.getSpec().getImageSource().getUpstream();
                boolean clusterOk = ups.getCluster() == null
                        || ups.getCluster().isBlank()
                        || ups.getCluster().equals(ownClusterName);
                if (clusterOk && selfName.equals(ups.getName()) && selfNamespace.equals(ups.getNamespace())) {
                    var ref = new DownstreamReference();
                    ref.setCluster(ownClusterName);
                    ref.setNamespace(ds.getMetadata().getNamespace());
                    ref.setName(ds.getMetadata().getName());
                    blockers.add(ref);
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to list ImageSources for downstream check on {}/{}: {}",
                    selfNamespace,
                    selfName,
                    e.getMessage());
        }

        for (DownstreamReference ref : readDownstreamReferences(cr)) {
            // Same-cluster entries are already handled by the K8s list above; keeping a stale
            // same-cluster entry would block deletion forever after the downstream is gone but
            // before the platform repaints the annotation. Trust the live K8s view for own
            // cluster; annotations only carry cross-cluster information.
            boolean isOwnCluster = ref.getCluster() != null && ref.getCluster().equals(ownClusterName);
            if (isOwnCluster) {
                continue;
            }
            if (!containsRef(blockers, ref)) {
                blockers.add(ref);
            }
        }
        return blockers;
    }

    private List<DownstreamReference> readDownstreamReferences(ImageSourceCrd cr) {
        if (cr.getMetadata() == null || cr.getMetadata().getAnnotations() == null) {
            return List.of();
        }
        String raw = cr.getMetadata().getAnnotations().get(Labels.ANNOTATION_DOWNSTREAM_REFERENCES);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<DownstreamReference> parsed =
                    objectMapper.readValue(raw, new TypeReference<List<DownstreamReference>>() {});
            return parsed != null ? parsed : List.of();
        } catch (Exception e) {
            log.warn(
                    "Failed to parse {} annotation on {}/{}: {}",
                    Labels.ANNOTATION_DOWNSTREAM_REFERENCES,
                    cr.getMetadata().getNamespace(),
                    cr.getMetadata().getName(),
                    e.getMessage());
            return List.of();
        }
    }

    private static boolean containsRef(List<DownstreamReference> refs, DownstreamReference ref) {
        for (var r : refs) {
            if (Objects.equals(r.getCluster(), ref.getCluster())
                    && Objects.equals(r.getNamespace(), ref.getNamespace())
                    && Objects.equals(r.getName(), ref.getName())) {
                return true;
            }
        }
        return false;
    }

    private static String formatBlockerMessage(List<DownstreamReference> blockers) {
        var sb = new StringBuilder("Blocked by ").append(blockers.size()).append(" downstream(s): ");
        for (int i = 0; i < blockers.size() && i < 5; i++) {
            if (i > 0) sb.append(", ");
            var b = blockers.get(i);
            sb.append(b.getCluster() == null ? "" : b.getCluster() + "/")
                    .append(b.getNamespace())
                    .append("/")
                    .append(b.getName());
        }
        if (blockers.size() > 5) {
            sb.append(" (+").append(blockers.size() - 5).append(" more)");
        }
        return sb.toString();
    }

    private void ensureUpstreamProtectionFinalizer(ImageSourceCrd cr, Context<ImageSourceCrd> context) {
        if (cr.getMetadata() == null) return;
        var existing = cr.getMetadata().getFinalizers();
        if (existing != null && existing.contains(Labels.FINALIZER_UPSTREAM_PROTECTION)) {
            return;
        }
        var updated = existing == null ? new ArrayList<String>() : new ArrayList<>(existing);
        updated.add(Labels.FINALIZER_UPSTREAM_PROTECTION);
        cr.getMetadata().setFinalizers(updated);
        try {
            context.getClient()
                    .resources(ImageSourceCrd.class)
                    .inNamespace(cr.getMetadata().getNamespace())
                    .withName(cr.getMetadata().getName())
                    .edit(existingCr -> {
                        var meta = existingCr.getMetadata();
                        var fin = meta.getFinalizers() == null
                                ? new ArrayList<String>()
                                : new ArrayList<>(meta.getFinalizers());
                        if (!fin.contains(Labels.FINALIZER_UPSTREAM_PROTECTION)) {
                            fin.add(Labels.FINALIZER_UPSTREAM_PROTECTION);
                            meta.setFinalizers(fin);
                        }
                        return existingCr;
                    });
        } catch (Exception e) {
            log.warn(
                    "Failed to persist {} finalizer on {}/{}: {}",
                    Labels.FINALIZER_UPSTREAM_PROTECTION,
                    cr.getMetadata().getNamespace(),
                    cr.getMetadata().getName(),
                    e.getMessage());
        }
    }

    private UpdateControl<ImageSourceCrd> reconcileGit(
            ImageSourceCrd cr,
            ImageSourceSpec spec,
            ImageSourceStatus prev,
            ImageSourceStatus next,
            String namespace,
            Context<ImageSourceCrd> context) {
        Instant now = Instant.now();
        try {
            String head = gitClient.resolveHead(
                    spec.getGit().getRepo(),
                    spec.getGit().getBranch(),
                    namespace,
                    spec.getGit().getCredentialsSecretRef());
            next.setObservedCommit(head);
            next.setLastPollAt(now);

            // Read the in-flight Job from the JOSDK secondary cache (populated by the dependent's
            // informer) — no manual kubeClient lookup. May be null while the workflow hasn't
            // materialized the Job yet, or if pendingBuild is itself empty.
            Job actualJob = context.getSecondaryResource(Job.class).orElse(null);
            buildOrchestrator.advance(cr, head, next, actualJob);

            // Make the dependent see the freshly-mutated status before the workflow runs. We
            // restore the original status before {@link #finalize} so its no-op detection
            // compares prev↔next, not next↔next.
            cr.setStatus(next);
            context.managedWorkflowAndDependentResourceContext().reconcileManagedWorkflow();
            cr.setStatus(prev);

            if (next.getLatestArtifact() != null && next.getLatestArtifact().getImageRef() != null) {
                applyReady(next, ImageSourceConditions.STATUS_TRUE, ImageSourceConditions.REASON_OBSERVED, null);
            } else if (next.getPendingBuild() != null) {
                applyReady(next, ImageSourceConditions.STATUS_FALSE, ImageSourceConditions.REASON_PENDING, null);
            } else {
                applyReady(next, ImageSourceConditions.STATUS_FALSE, ImageSourceConditions.REASON_PENDING, null);
            }
        } catch (Exception e) {
            log.warn(
                    "ImageSource {}/{} poll failed: {}",
                    cr.getMetadata().getNamespace(),
                    cr.getMetadata().getName(),
                    e.getMessage());
            next.setLastPollAt(now);
            applyReady(
                    next,
                    ImageSourceConditions.STATUS_FALSE,
                    ImageSourceConditions.REASON_POLL_FAILED,
                    truncate(e.getMessage()));
        }
        UpdateControl<ImageSourceCrd> control = finalize(cr, prev, next);
        return control.rescheduleAfter(intervalSeconds(spec, next, Instant.now()), TimeUnit.SECONDS);
    }

    private UpdateControl<ImageSourceCrd> reconcileImage(
            ImageSourceCrd cr, ImageSourceSpec spec, ImageSourceStatus prev, ImageSourceStatus next) {
        var artifact = new LatestArtifact();
        artifact.setImageRef(spec.getImage().getRef());
        artifact.setBuiltAt(Instant.now());
        next.setLatestArtifact(artifact);
        applyReady(next, ImageSourceConditions.STATUS_TRUE, ImageSourceConditions.REASON_PINNED, null);
        UpdateControl<ImageSourceCrd> control = finalize(cr, prev, next);
        // Image-type ImageSources don't need fast polling — schedule a slow re-reconcile so
        // observability stays alive without burning the apiserver.
        return control.rescheduleAfter(IMAGE_TYPE_RESCHEDULE_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Reconcile a {@code type: imageSource} (promotion) ImageSource. Two paths:
     *
     * <ul>
     *   <li><b>Same cluster:</b> the operator reads the upstream {@link ImageSourceCrd} via the
     *       fabric8 client. {@code autoPromote=true} mirrors {@code upstream.status.latestArtifact}
     *       on every reconcile; {@code autoPromote=false} mirrors only when the spec's
     *       {@code pinnedDigest} matches the upstream's current digest.
     *   <li><b>Cross-cluster:</b> the operator can't see the upstream cluster — the platform brokers
     *       digest changes by setting {@code spec.imageSource.pinnedDigest}. The operator simply
     *       reads pinnedDigest and writes it as {@code latestArtifact.imageRef} (BYO registry: both
     *       clusters reach the same registry, so the digest is sufficient).
     * </ul>
     */
    private UpdateControl<ImageSourceCrd> reconcileImageSource(
            ImageSourceCrd cr,
            ImageSourceSpec spec,
            ImageSourceStatus prev,
            ImageSourceStatus next,
            Context<ImageSourceCrd> context) {
        ImageSourcePromotionSpec promo = spec.getImageSource();
        ImageSourceUpstreamSpec upstream = promo.getUpstream();
        boolean sameCluster = upstream.getCluster() == null
                || upstream.getCluster().isBlank()
                || upstream.getCluster().equals(ownClusterName);
        if (sameCluster) {
            applyImageSourceSameCluster(cr, promo, upstream, next, context);
        } else {
            applyImageSourceCrossCluster(promo, next);
        }
        UpdateControl<ImageSourceCrd> control = finalize(cr, prev, next);
        return control.rescheduleAfter(IMAGE_SOURCE_TYPE_RESCHEDULE_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Same-cluster: read the upstream ImageSource directly from the K8s client. Mirror its
     * {@code status.latestArtifact} into ours, gated by autoPromote / pinnedDigest. Conservative
     * upstream-deletion behavior: keep the last-known artifact and surface {@code UpstreamMissing}.
     */
    private void applyImageSourceSameCluster(
            ImageSourceCrd cr,
            ImageSourcePromotionSpec promo,
            ImageSourceUpstreamSpec upstream,
            ImageSourceStatus next,
            Context<ImageSourceCrd> context) {
        ImageSourceCrd upstreamCr;
        try {
            upstreamCr = context.getClient()
                    .resources(ImageSourceCrd.class)
                    .inNamespace(upstream.getNamespace())
                    .withName(upstream.getName())
                    .get();
        } catch (Exception e) {
            log.warn(
                    "Failed to fetch upstream ImageSource {}/{}: {}",
                    upstream.getNamespace(),
                    upstream.getName(),
                    e.getMessage());
            removeCondition(next, ImageSourceConditions.TYPE_UPSTREAM_NOT_READY);
            upsertCondition(
                    next,
                    ImageSourceConditions.TYPE_UPSTREAM_MISSING,
                    ImageSourceConditions.STATUS_TRUE,
                    ImageSourceConditions.REASON_UPSTREAM_GONE,
                    truncate(e.getMessage()));
            applyReadyForImageSource(next);
            return;
        }
        if (upstreamCr == null) {
            // Upstream gone — keep last-known artifact, surface UpstreamMissing.
            removeCondition(next, ImageSourceConditions.TYPE_UPSTREAM_NOT_READY);
            upsertCondition(
                    next,
                    ImageSourceConditions.TYPE_UPSTREAM_MISSING,
                    ImageSourceConditions.STATUS_TRUE,
                    ImageSourceConditions.REASON_UPSTREAM_GONE,
                    "Upstream ImageSource " + upstream.getNamespace() + "/" + upstream.getName() + " not found");
            applyReadyForImageSource(next);
            return;
        }
        removeCondition(next, ImageSourceConditions.TYPE_UPSTREAM_MISSING);
        LatestArtifact upstreamArtifact =
                upstreamCr.getStatus() != null ? upstreamCr.getStatus().getLatestArtifact() : null;
        if (upstreamArtifact == null
                || upstreamArtifact.getImageRef() == null
                || upstreamArtifact.getImageRef().isBlank()) {
            upsertCondition(
                    next,
                    ImageSourceConditions.TYPE_UPSTREAM_NOT_READY,
                    ImageSourceConditions.STATUS_TRUE,
                    ImageSourceConditions.REASON_NO_UPSTREAM_ARTIFACT,
                    "Upstream " + upstream.getNamespace() + "/" + upstream.getName() + " has no latestArtifact yet");
            applyReadyForImageSource(next);
            return;
        }
        removeCondition(next, ImageSourceConditions.TYPE_UPSTREAM_NOT_READY);
        boolean autoPromote = Boolean.TRUE.equals(promo.getAutoPromote());
        if (autoPromote) {
            // Mirror upstream's artifact (digest + sourceCommit).
            mirrorArtifact(next, upstreamArtifact);
            applyReady(next, ImageSourceConditions.STATUS_TRUE, ImageSourceConditions.REASON_PROMOTED, null);
            return;
        }
        // Manual pin: mirror the pinned digest directly. When pinnedDigest matches the upstream's
        // current artifact we copy the full pair (digest, sourceCommit); otherwise we still pin
        // to the digest alone — the operator trusts it's reachable in the registry (BYO registry
        // assumption — same as the cross-cluster path).
        String pinned = promo.getPinnedDigest();
        if (pinned == null || pinned.isBlank()) {
            upsertCondition(
                    next,
                    ImageSourceConditions.TYPE_UPSTREAM_NOT_READY,
                    ImageSourceConditions.STATUS_TRUE,
                    ImageSourceConditions.REASON_AWAITING_PIN,
                    "autoPromote=false and no pinnedDigest set");
            applyReadyForImageSource(next);
            return;
        }
        if (digestMatches(upstreamArtifact.getImageRef(), pinned)) {
            mirrorArtifact(next, upstreamArtifact);
        } else {
            var artifact = new LatestArtifact();
            artifact.setImageRef(pinned);
            artifact.setBuiltAt(Instant.now());
            next.setLatestArtifact(artifact);
        }
        applyReady(next, ImageSourceConditions.STATUS_TRUE, ImageSourceConditions.REASON_PINNED, null);
    }

    /**
     * Cross-cluster: trust {@code spec.imageSource.pinnedDigest} as the source of truth. The
     * platform broker sets pinnedDigest based on observed upstream changes (under
     * {@code autoPromote=true}) or manual promote actions ({@code autoPromote=false}). The operator
     * simply mirrors pinnedDigest into {@code status.latestArtifact} — BYO registry assumption
     * means the digest is reachable from this cluster's nodes.
     */
    private void applyImageSourceCrossCluster(ImageSourcePromotionSpec promo, ImageSourceStatus next) {
        String pinned = promo.getPinnedDigest();
        if (pinned == null || pinned.isBlank()) {
            upsertCondition(
                    next,
                    ImageSourceConditions.TYPE_UPSTREAM_NOT_READY,
                    ImageSourceConditions.STATUS_TRUE,
                    ImageSourceConditions.REASON_AWAITING_PIN,
                    "Cross-cluster ImageSource: waiting for platform to set pinnedDigest");
            applyReadyForImageSource(next);
            return;
        }
        removeCondition(next, ImageSourceConditions.TYPE_UPSTREAM_NOT_READY);
        removeCondition(next, ImageSourceConditions.TYPE_UPSTREAM_MISSING);
        var artifact = next.getLatestArtifact() != null ? next.getLatestArtifact() : new LatestArtifact();
        // pinnedDigest may be either a bare "sha256:..." digest or a full "registry/path@sha256:..."
        // ref — pass through as imageRef when it's already a full ref, otherwise this is just the
        // digest part and we have nothing else to attach.
        artifact.setImageRef(pinned);
        artifact.setBuiltAt(Instant.now());
        next.setLatestArtifact(artifact);
        applyReady(next, ImageSourceConditions.STATUS_TRUE, ImageSourceConditions.REASON_PROMOTED, null);
    }

    private static void mirrorArtifact(ImageSourceStatus next, LatestArtifact upstream) {
        var copy = new LatestArtifact();
        copy.setImageRef(upstream.getImageRef());
        copy.setSourceCommit(upstream.getSourceCommit());
        copy.setBuiltAt(Instant.now());
        next.setLatestArtifact(copy);
    }

    /**
     * Returns true when {@code imageRef} ends with the given digest, or contains it as a substring
     * (callers may pass either a full {@code repo@sha256:...} ref or a bare {@code sha256:...}).
     */
    private static boolean digestMatches(String imageRef, String digest) {
        if (imageRef == null || digest == null) return false;
        return imageRef.equals(digest) || imageRef.endsWith("@" + digest) || imageRef.endsWith(digest);
    }

    /**
     * Set Ready=False/Pending when an imageSource-type spec doesn't yet have a usable artifact.
     * Skips the override when we've already mirrored a prior artifact (that path sets Ready=True).
     */
    private static void applyReadyForImageSource(ImageSourceStatus next) {
        if (next.getLatestArtifact() != null && next.getLatestArtifact().getImageRef() != null) {
            applyReady(next, ImageSourceConditions.STATUS_TRUE, ImageSourceConditions.REASON_PROMOTED, null);
        } else {
            applyReady(next, ImageSourceConditions.STATUS_FALSE, ImageSourceConditions.REASON_PENDING, null);
        }
    }

    /**
     * Returns an error message when the spec violates the "exactly one sub-block matches type"
     * invariant. The CRD schema doesn't enforce this — see Fork 1(a) decision in #109 PR1 — so
     * the operator validates and surfaces a {@code ConfigInvalid} condition.
     */
    static Optional<String> validate(ImageSourceSpec spec) {
        if (spec.getType() == null) {
            return Optional.of("spec.type is required");
        }
        return switch (spec.getType()) {
            case GIT -> {
                if (spec.getGit() == null) {
                    yield Optional.of("spec.type=git but spec.git is missing");
                }
                if (spec.getGit().getRepo() == null || spec.getGit().getRepo().isBlank()) {
                    yield Optional.of("spec.git.repo is required");
                }
                if (spec.getImage() != null) {
                    yield Optional.of("spec.image must not be set when type=git");
                }
                if (spec.getImageSource() != null) {
                    yield Optional.of("spec.imageSource must not be set when type=git");
                }
                yield Optional.empty();
            }
            case IMAGE -> {
                if (spec.getImage() == null) {
                    yield Optional.of("spec.type=image but spec.image is missing");
                }
                if (spec.getImage().getRef() == null || spec.getImage().getRef().isBlank()) {
                    yield Optional.of("spec.image.ref is required");
                }
                if (spec.getGit() != null) {
                    yield Optional.of("spec.git must not be set when type=image");
                }
                if (spec.getImageSource() != null) {
                    yield Optional.of("spec.imageSource must not be set when type=image");
                }
                yield Optional.empty();
            }
            case IMAGE_SOURCE -> {
                ImageSourcePromotionSpec promo = spec.getImageSource();
                if (promo == null) {
                    yield Optional.of("spec.type=imageSource but spec.imageSource is missing");
                }
                ImageSourceUpstreamSpec upstream = promo.getUpstream();
                if (upstream == null) {
                    yield Optional.of("spec.imageSource.upstream is required");
                }
                if (upstream.getName() == null || upstream.getName().isBlank()) {
                    yield Optional.of("spec.imageSource.upstream.name is required");
                }
                if (upstream.getNamespace() == null || upstream.getNamespace().isBlank()) {
                    yield Optional.of("spec.imageSource.upstream.namespace is required");
                }
                boolean autoPromote = Boolean.TRUE.equals(promo.getAutoPromote());
                boolean hasPin = promo.getPinnedDigest() != null
                        && !promo.getPinnedDigest().isBlank();
                if (autoPromote && hasPin) {
                    yield Optional.of("spec.imageSource: autoPromote=true and pinnedDigest are mutually exclusive");
                }
                if (!autoPromote && !hasPin) {
                    yield Optional.of("spec.imageSource: exactly one of autoPromote=true or pinnedDigest must be set");
                }
                if (spec.getGit() != null || spec.getImage() != null) {
                    yield Optional.of("spec.git/spec.image must not be set when type=imageSource");
                }
                yield Optional.empty();
            }
        };
    }

    private UpdateControl<ImageSourceCrd> finalize(ImageSourceCrd cr, ImageSourceStatus prev, ImageSourceStatus next) {
        if (statusEquals(prev, next)) {
            return UpdateControl.noUpdate();
        }
        cr.setStatus(next);
        try {
            eventPublisher.emitImageSourceSync(cr);
        } catch (Exception e) {
            log.warn(
                    "Failed to enqueue sync for ImageSource {}: {}",
                    cr.getMetadata().getName(),
                    e.getMessage());
        }
        return UpdateControl.patchStatus(cr);
    }

    /**
     * Pick the next reschedule cadence. When a webhook arrived within the freshness window,
     * fall back to {@code intervalSecondsAfterWebhook} (slow); otherwise the standard
     * {@code intervalSeconds} cadence. Net effect: a connected repo with a working webhook
     * generates near-zero polling traffic; a disconnected repo keeps the 60s default.
     */
    static int intervalSeconds(ImageSourceSpec spec, ImageSourceStatus status, Instant now) {
        var trigger = spec.getTrigger();
        var poll = trigger != null ? trigger.getPoll() : null;
        int fast = poll != null && poll.getIntervalSeconds() != null && poll.getIntervalSeconds() > 0
                ? poll.getIntervalSeconds()
                : DEFAULT_INTERVAL_SECONDS;
        Instant lastWebhookAt = status != null ? status.getLastWebhookAt() : null;
        if (lastWebhookAt == null) {
            return fast;
        }
        int freshness =
                poll != null && poll.getWebhookFreshnessSeconds() != null && poll.getWebhookFreshnessSeconds() > 0
                        ? poll.getWebhookFreshnessSeconds()
                        : DEFAULT_WEBHOOK_FRESHNESS_SECONDS;
        if (now.isAfter(lastWebhookAt.plusSeconds(freshness))) {
            return fast;
        }
        int slow = poll != null
                        && poll.getIntervalSecondsAfterWebhook() != null
                        && poll.getIntervalSecondsAfterWebhook() > 0
                ? poll.getIntervalSecondsAfterWebhook()
                : DEFAULT_INTERVAL_AFTER_WEBHOOK_SECONDS;
        // Never go *slower* than the user-asked-for fast cadence (a misconfigured slow value
        // shouldn't break their visibility into a freshly-connected repo).
        return Math.max(slow, fast);
    }

    private static void applyReady(ImageSourceStatus status, String boolStatus, String reason, String message) {
        // Reset conditions list so ConfigInvalid (if previously set) is cleared on transition
        // back to a valid spec.
        upsertCondition(status, ImageSourceConditions.TYPE_READY, boolStatus, reason, message);
        removeCondition(status, ImageSourceConditions.TYPE_CONFIG_INVALID);
    }

    private static void applyConfigInvalid(ImageSourceStatus status, String message) {
        upsertCondition(
                status,
                ImageSourceConditions.TYPE_CONFIG_INVALID,
                ImageSourceConditions.STATUS_TRUE,
                ImageSourceConditions.REASON_BAD_SPEC,
                message);
        upsertCondition(
                status,
                ImageSourceConditions.TYPE_READY,
                ImageSourceConditions.STATUS_FALSE,
                ImageSourceConditions.REASON_BAD_SPEC,
                null);
    }

    private static void upsertCondition(
            ImageSourceStatus status, String type, String boolStatus, String reason, String message) {
        List<ImageSourceCondition> conditions =
                status.getConditions() != null ? new ArrayList<>(status.getConditions()) : new ArrayList<>();
        ImageSourceCondition existing = conditions.stream()
                .filter(c -> type.equals(c.getType()))
                .findFirst()
                .orElse(null);
        Instant now = Instant.now();
        if (existing == null) {
            var fresh = new ImageSourceCondition();
            fresh.setType(type);
            fresh.setStatus(boolStatus);
            fresh.setReason(reason);
            fresh.setMessage(message);
            fresh.setLastTransitionTime(now);
            conditions.add(fresh);
        } else {
            // Only stamp lastTransitionTime when the truthy status actually changed; reason or
            // message changes on a stable status keep the original transition time.
            if (!Objects.equals(existing.getStatus(), boolStatus)) {
                existing.setLastTransitionTime(now);
            }
            existing.setStatus(boolStatus);
            existing.setReason(reason);
            existing.setMessage(message);
        }
        status.setConditions(conditions);
    }

    private static void removeCondition(ImageSourceStatus status, String type) {
        if (status.getConditions() == null) {
            return;
        }
        List<ImageSourceCondition> filtered = new ArrayList<>();
        for (var c : status.getConditions()) {
            if (!type.equals(c.getType())) {
                filtered.add(c);
            }
        }
        status.setConditions(filtered.isEmpty() ? null : filtered);
    }

    /**
     * Status-equality check that excludes {@code lastPollAt} — it changes every tick and
     * would force a no-op patch every reconcile. Conditions are compared by (type, status,
     * reason, message); transition time alone doesn't drive a re-emit.
     */
    static boolean statusEquals(ImageSourceStatus a, ImageSourceStatus b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getObservedCommit(), b.getObservedCommit())
                && Objects.equals(a.getObservedGeneration(), b.getObservedGeneration())
                && Objects.equals(a.getLastWebhookAt(), b.getLastWebhookAt())
                && Objects.equals(a.getLatestArtifact(), b.getLatestArtifact())
                && pendingBuildEquals(a.getPendingBuild(), b.getPendingBuild())
                && pendingBuildEquals(a.getQueuedBuild(), b.getQueuedBuild())
                && conditionsEqual(a.getConditions(), b.getConditions());
    }

    private static boolean pendingBuildEquals(PendingBuild a, PendingBuild b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getSourceCommit(), b.getSourceCommit())
                && Objects.equals(a.getLifecycle(), b.getLifecycle())
                && Objects.equals(a.getDeploymentId(), b.getDeploymentId())
                && Objects.equals(a.getJobName(), b.getJobName())
                && Objects.equals(a.getErrorMessage(), b.getErrorMessage());
    }

    private static boolean conditionsEqual(List<ImageSourceCondition> a, List<ImageSourceCondition> b) {
        if (a == null && b == null) return true;
        int sa = a == null ? 0 : a.size();
        int sb = b == null ? 0 : b.size();
        if (sa != sb) return false;
        for (int i = 0; i < sa; i++) {
            ImageSourceCondition ca = a.get(i);
            ImageSourceCondition cb = b.get(i);
            if (!Objects.equals(ca.getType(), cb.getType())
                    || !Objects.equals(ca.getStatus(), cb.getStatus())
                    || !Objects.equals(ca.getReason(), cb.getReason())
                    || !Objects.equals(ca.getMessage(), cb.getMessage())) {
                return false;
            }
        }
        return true;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 512 ? message.substring(0, 512) + "..." : message;
    }
}

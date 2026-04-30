package eu.appbahn.operator.reconciler.imagesource;

import eu.appbahn.operator.reconciler.imagesource.buildjob.BuildJobDependentResource;
import eu.appbahn.operator.reconciler.imagesource.buildjob.BuildJobReconcileCondition;
import eu.appbahn.operator.reconciler.imagesource.buildjob.BuildOrchestrator;
import eu.appbahn.operator.tunnel.OperatorEventPublisher;
import eu.appbahn.operator.tunnel.OperatorTunnelConfig;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourceCondition;
import eu.appbahn.shared.crd.imagesource.ImageSourceConditions;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourcePromotionSpec;
import eu.appbahn.shared.crd.imagesource.ImageSourceSpec;
import eu.appbahn.shared.crd.imagesource.ImageSourceStatus;
import eu.appbahn.shared.crd.imagesource.ImageSourceUpstreamSpec;
import eu.appbahn.shared.crd.imagesource.LatestArtifact;
import eu.appbahn.shared.crd.imagesource.PendingBuild;
import io.fabric8.kubernetes.api.model.HasMetadata;
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
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
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
    static final int IMAGE_TYPE_RESCHEDULE_SECONDS = 300;
    static final int IMAGE_SOURCE_TYPE_RESCHEDULE_SECONDS = 30;

    private final GitClient gitClient;
    private final OperatorEventPublisher eventPublisher;
    private final BuildOrchestrator buildOrchestrator;
    private final String ownClusterName;

    /**
     * Cache of all watched Resources, populated by the informer event source. Used by
     * {@link #ensureOwnerReference} to find Resources referencing this ImageSource without
     * a per-reconcile server-side {@code list()} call. Wired in {@link #prepareEventSources}.
     */
    private InformerEventSource<ResourceCrd, ImageSourceCrd> resourceCache;

    public ImageSourceReconciler(
            GitClient gitClient,
            OperatorEventPublisher eventPublisher,
            BuildOrchestrator buildOrchestrator,
            OperatorTunnelConfig tunnelConfig) {
        this.gitClient = gitClient;
        this.eventPublisher = eventPublisher;
        this.buildOrchestrator = buildOrchestrator;
        this.ownClusterName = tunnelConfig.clusterName();
    }

    /**
     * Watch sibling Resources so an arriving Resource triggers the OwnerReference auto-bind
     * without waiting for the slow re-reconcile schedule. Also watch upstream ImageSources so a
     * downstream {@code type: imageSource} promotion reconciles promptly when the upstream's
     * {@code latestArtifact} changes. Build Jobs are watched via the {@code @Workflow} dependent
     * (see {@link BuildJobDependentResource}).
     *
     * <p>The Resource→ImageSource event source uses {@link RebindAwareInformerEventSource} so a
     * Resource flipping its bound ImageSource also reconciles the previously-bound ImageSource —
     * otherwise the old ImageSource keeps its stale {@code OwnerReference} until its slow periodic
     * reschedule fires.
     */
    @Override
    public List<EventSource<?, ImageSourceCrd>> prepareEventSources(EventSourceContext<ImageSourceCrd> context) {
        SecondaryToPrimaryMapper<ResourceCrd> resourceMapper = resource -> {
            if (resource.getMetadata() == null
                    || resource.getSpec() == null
                    || resource.getSpec().getRelease() == null
                    || resource.getSpec().getRelease().getFromImageSource() == null) {
                return Set.of();
            }
            String boundName =
                    resource.getSpec().getRelease().getFromImageSource().getName();
            if (boundName == null || boundName.isBlank()) {
                return Set.of();
            }
            return Set.of(new ResourceID(boundName, resource.getMetadata().getNamespace()));
        };
        var resourceInformerConfig = InformerEventSourceConfiguration.from(ResourceCrd.class, ImageSourceCrd.class)
                .withSecondaryToPrimaryMapper(resourceMapper)
                .build();
        // Upstream ImageSource → all downstream ImageSources whose spec.imageSource.upstream
        // points at it (cluster matches own; namespace+name match the upstream CR).
        var upstreamInformerConfig = InformerEventSourceConfiguration.from(ImageSourceCrd.class, ImageSourceCrd.class)
                .withSecondaryToPrimaryMapper(upstreamCr -> {
                    if (upstreamCr.getMetadata() == null) return Set.of();
                    String upstreamName = upstreamCr.getMetadata().getName();
                    String upstreamNs = upstreamCr.getMetadata().getNamespace();
                    var downstreams = context.getClient()
                            .resources(ImageSourceCrd.class)
                            .inAnyNamespace()
                            .list()
                            .getItems();
                    var hits = new java.util.HashSet<ResourceID>();
                    for (var ds : downstreams) {
                        if (ds.getSpec() == null
                                || ds.getSpec().getType()
                                        != eu.appbahn.shared.crd.imagesource.ImageSourceType.IMAGE_SOURCE
                                || ds.getSpec().getImageSource() == null
                                || ds.getSpec().getImageSource().getUpstream() == null) {
                            continue;
                        }
                        var ups = ds.getSpec().getImageSource().getUpstream();
                        boolean clusterOk = ups.getCluster() == null
                                || ups.getCluster().isBlank()
                                || ups.getCluster().equals(ownClusterName);
                        if (clusterOk && upstreamName.equals(ups.getName()) && upstreamNs.equals(ups.getNamespace())) {
                            hits.add(new ResourceID(
                                    ds.getMetadata().getName(), ds.getMetadata().getNamespace()));
                        }
                    }
                    return hits;
                })
                .build();
        var resourceSource = new RebindAwareInformerEventSource<>(resourceInformerConfig, context, resourceMapper);
        this.resourceCache = resourceSource;
        return List.of(resourceSource, new InformerEventSource<>(upstreamInformerConfig, context));
    }

    /**
     * {@link InformerEventSource} that, on a secondary-resource update, also fires events for any
     * primary IDs the OLD value mapped to but the new value no longer maps to. This guarantees
     * that when a {@code Resource} re-binds from {@code ImageSource X} to {@code ImageSource Y},
     * X reconciles immediately (and clears its stale OwnerReference) instead of waiting for its
     * next periodic reschedule.
     */
    static class RebindAwareInformerEventSource<R extends HasMetadata, P extends HasMetadata>
            extends InformerEventSource<R, P> {

        private final SecondaryToPrimaryMapper<R> mapper;

        RebindAwareInformerEventSource(
                InformerEventSourceConfiguration<R> config,
                EventSourceContext<P> context,
                SecondaryToPrimaryMapper<R> mapper) {
            super(config, context);
            this.mapper = mapper;
        }

        @Override
        public void onUpdate(R oldObject, R newObject) {
            super.onUpdate(oldObject, newObject);
            Set<ResourceID> oldIds = mapper.toPrimaryResourceIDs(oldObject);
            if (oldIds.isEmpty()) {
                return;
            }
            Set<ResourceID> newIds = mapper.toPrimaryResourceIDs(newObject);
            EventHandler handler = getEventHandler();
            if (handler == null) {
                return;
            }
            for (ResourceID oldId : oldIds) {
                if (!newIds.contains(oldId)) {
                    handler.handleEvent(new Event(oldId));
                }
            }
        }
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
     * Reconcile the ImageSource's {@code metadata.ownerReferences} with the current set of
     * sibling Resources whose {@code spec.release.fromImageSource.name} matches. Strict 1:1.
     *
     * <ul>
     *   <li>Exactly 1 match, no controller owner yet → stamp the owner reference.</li>
     *   <li>Exactly 1 match, controller owner already correct → no-op.</li>
     *   <li>Exactly 1 match, controller owner points at a DIFFERENT (or stale) Resource →
     *       rewrite the owner reference. This is the re-bind path: a Resource flipping its bound
     *       ImageSource leaves the old IS pointing at the wrong owner; without the rewrite, the
     *       stale owner could cascade-delete the IS when the (re-bound, no longer related)
     *       Resource is deleted.</li>
     *   <li>0 matches → clear our controller owner reference (if any) and surface
     *       {@code OwnerNotResolved/NoMatch}.</li>
     *   <li>&gt;1 matches → ambiguous; surface {@code OwnerNotResolved/Ambiguous} and leave the
     *       existing reference untouched.</li>
     * </ul>
     *
     * <p>Resources are read from the informer cache populated by {@link
     * RebindAwareInformerEventSource} — no per-reconcile server-side {@code list()}.
     */
    private void ensureOwnerReference(ImageSourceCrd cr, Context<ImageSourceCrd> context, ImageSourceStatus next) {
        String namespace = cr.getMetadata().getNamespace();
        String name = cr.getMetadata().getName();
        List<ResourceCrd> matches = findReferencingResources(namespace, name);

        if (matches.size() > 1) {
            upsertCondition(
                    next,
                    ImageSourceConditions.TYPE_OWNER_NOT_RESOLVED,
                    ImageSourceConditions.STATUS_TRUE,
                    ImageSourceConditions.REASON_AMBIGUOUS,
                    matches.size() + " Resources in namespace " + namespace + " reference this ImageSource");
            return;
        }

        if (matches.isEmpty()) {
            // Drop our stale controller owner if one exists (Resource was re-bound or removed).
            if (clearControllerOwnerReference(cr, context, namespace, name)) {
                log.info("Cleared stale controller OwnerReference on ImageSource {}/{}", namespace, name);
            }
            upsertCondition(
                    next,
                    ImageSourceConditions.TYPE_OWNER_NOT_RESOLVED,
                    ImageSourceConditions.STATUS_TRUE,
                    ImageSourceConditions.REASON_NO_MATCH,
                    "No Resource in namespace " + namespace + " references this ImageSource");
            return;
        }

        ResourceCrd owner = matches.get(0);
        String uid = owner.getMetadata() != null ? owner.getMetadata().getUid() : null;
        if (uid == null || uid.isBlank()) {
            log.debug(
                    "Owner Resource {}/{} has no UID yet; deferring owner-bind",
                    namespace,
                    owner.getMetadata() != null ? owner.getMetadata().getName() : "?");
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
     * List Resources from the informer cache that reference this ImageSource by name in the same
     * namespace. Returns an empty list if the cache isn't initialised yet (operator starting up)
     * or if reading the cache fails.
     */
    private List<ResourceCrd> findReferencingResources(String namespace, String name) {
        if (resourceCache == null) {
            return List.of();
        }
        try {
            return resourceCache
                    .list(r -> r.getMetadata() != null
                            && namespace.equals(r.getMetadata().getNamespace())
                            && r.getSpec() != null
                            && r.getSpec().getRelease() != null
                            && r.getSpec().getRelease().getFromImageSource() != null
                            && name.equals(r.getSpec()
                                    .getRelease()
                                    .getFromImageSource()
                                    .getName()))
                    .toList();
        } catch (Exception e) {
            log.debug("Resource cache read failed for {}/{}: {}", namespace, name, e.getMessage());
            return List.of();
        }
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
     * Drop a controller {@link OwnerReference} pointing at an {@code appbahn.eu/v1 Resource}.
     * Other unrelated owner references (if any) are preserved. Returns true when something was
     * removed (signal for a log line); false when there was no Resource-controller owner to
     * clear.
     */
    private static boolean clearControllerOwnerReference(
            ImageSourceCrd cr, Context<ImageSourceCrd> context, String namespace, String name) {
        if (cr.getMetadata() == null || cr.getMetadata().getOwnerReferences() == null) {
            return false;
        }
        String resourceApiVersion = HasMetadata.getApiVersion(ResourceCrd.class);
        String resourceKind = HasMetadata.getKind(ResourceCrd.class);
        boolean hasStaleResourceOwner = cr.getMetadata().getOwnerReferences().stream()
                .anyMatch(o -> Boolean.TRUE.equals(o.getController())
                        && resourceKind.equals(o.getKind())
                        && resourceApiVersion.equals(o.getApiVersion()));
        if (!hasStaleResourceOwner) {
            return false;
        }
        java.util.List<OwnerReference> retained = cr.getMetadata().getOwnerReferences().stream()
                .filter(o -> !(Boolean.TRUE.equals(o.getController())
                        && resourceKind.equals(o.getKind())
                        && resourceApiVersion.equals(o.getApiVersion())))
                .toList();
        cr.getMetadata().setOwnerReferences(retained);
        try {
            context.getClient()
                    .resources(ImageSourceCrd.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .edit(existing -> {
                        existing.getMetadata().setOwnerReferences(retained);
                        return existing;
                    });
        } catch (Exception e) {
            log.warn("Failed to clear stale OwnerReference on ImageSource {}/{}: {}", namespace, name, e.getMessage());
        }
        return true;
    }

    @Override
    public DeleteControl cleanup(ImageSourceCrd cr, Context<ImageSourceCrd> context) {
        String name = cr.getMetadata().getName();
        try {
            eventPublisher.emitImageSourceDeleted(name);
            log.info("Notified platform of ImageSource deletion: {}", name);
        } catch (Exception e) {
            log.warn("Failed to notify platform of ImageSource deletion {}: {}", name, e.getMessage());
        }
        return DeleteControl.defaultDelete();
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
        return control.rescheduleAfter(intervalSeconds(spec), TimeUnit.SECONDS);
    }

    private UpdateControl<ImageSourceCrd> reconcileImage(
            ImageSourceCrd cr, ImageSourceSpec spec, ImageSourceStatus prev, ImageSourceStatus next) {
        var artifact = new LatestArtifact();
        artifact.setImageRef(spec.getImage().getRef());
        artifact.setRunCommand(spec.getImage().getRunCommand());
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
            // Mirror upstream's full artifact triple (digest, runCommand, sourceCommit).
            mirrorArtifact(next, upstreamArtifact);
            applyReady(next, ImageSourceConditions.STATUS_TRUE, ImageSourceConditions.REASON_PROMOTED, null);
            return;
        }
        // Manual pin: mirror the pinned digest directly. When pinnedDigest matches the upstream's
        // current artifact we copy the full triple (digest, runCommand, sourceCommit); otherwise
        // we still pin to the digest alone — the operator trusts it's reachable in the registry
        // (BYO registry assumption — same as the cross-cluster path).
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
        copy.setRunCommand(upstream.getRunCommand());
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

    private static int intervalSeconds(ImageSourceSpec spec) {
        var trigger = spec.getTrigger();
        if (trigger != null
                && trigger.getPoll() != null
                && trigger.getPoll().getIntervalSeconds() != null
                && trigger.getPoll().getIntervalSeconds() > 0) {
            return trigger.getPoll().getIntervalSeconds();
        }
        return DEFAULT_INTERVAL_SECONDS;
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

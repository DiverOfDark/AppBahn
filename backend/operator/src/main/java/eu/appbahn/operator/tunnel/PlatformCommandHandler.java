package eu.appbahn.operator.tunnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.operator.reconciler.imagesource.buildjob.BuildOrchestrator;
import eu.appbahn.operator.tunnel.client.model.AckCommandRequest;
import eu.appbahn.operator.tunnel.client.model.AdminConfigPush;
import eu.appbahn.operator.tunnel.client.model.ApplyNamespace;
import eu.appbahn.operator.tunnel.client.model.ApplyResourceBundle;
import eu.appbahn.operator.tunnel.client.model.CancelBuild;
import eu.appbahn.operator.tunnel.client.model.ClusterCapacityResult;
import eu.appbahn.operator.tunnel.client.model.CommandResponse;
import eu.appbahn.operator.tunnel.client.model.CommandResponse.StatusEnum;
import eu.appbahn.operator.tunnel.client.model.DeleteNamespace;
import eu.appbahn.operator.tunnel.client.model.DeleteResource;
import eu.appbahn.operator.tunnel.client.model.HelloAck;
import eu.appbahn.operator.tunnel.client.model.ListPods;
import eu.appbahn.operator.tunnel.client.model.ListPodsResult;
import eu.appbahn.operator.tunnel.client.model.MetricsResult;
import eu.appbahn.operator.tunnel.client.model.NudgeImageSource;
import eu.appbahn.operator.tunnel.client.model.QueryClusterCapacity;
import eu.appbahn.operator.tunnel.client.model.QueryMetrics;
import eu.appbahn.operator.tunnel.client.model.QuotaRbacCachePush;
import eu.appbahn.operator.tunnel.client.model.ResourceDeletedBatch;
import eu.appbahn.operator.tunnel.client.model.RetryBuild;
import eu.appbahn.operator.tunnel.query.ClusterCapacityQuery;
import eu.appbahn.operator.tunnel.query.PodInfoQuery;
import eu.appbahn.operator.tunnel.query.PrometheusQueryService;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourceSpec;
import eu.appbahn.shared.crd.imagesource.ImageSourceStatus;
import eu.appbahn.shared.crd.imagesource.ImageSourceType;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dispatches SSE frames received from the tunnel's commands stream into concrete actions
 * on this cluster. Each command's outcome is reported back via {@link TunnelApiClient#ackCommand}.
 */
@Service
public class PlatformCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(PlatformCommandHandler.class);

    private final KubernetesClient kubernetesClient;
    private final TunnelApiClient tunnelApiClient;
    private final AdmissionSnapshotCache admissionCache;
    private final AdminConfigCache adminConfigCache;
    private final OperatorEventPublisher eventPublisher;
    private final BuildOrchestrator buildOrchestrator;
    private final ObjectMapper mapper;
    private final PodInfoQuery podInfoQuery;
    private final ClusterCapacityQuery clusterCapacityQuery;
    private final PrometheusQueryService prometheusQueryService;

    public PlatformCommandHandler(
            KubernetesClient kubernetesClient,
            TunnelApiClient tunnelApiClient,
            AdmissionSnapshotCache admissionCache,
            AdminConfigCache adminConfigCache,
            OperatorEventPublisher eventPublisher,
            BuildOrchestrator buildOrchestrator,
            ObjectMapper mapper,
            PodInfoQuery podInfoQuery,
            ClusterCapacityQuery clusterCapacityQuery,
            PrometheusQueryService prometheusQueryService) {
        this.kubernetesClient = kubernetesClient;
        this.tunnelApiClient = tunnelApiClient;
        this.admissionCache = admissionCache;
        this.adminConfigCache = adminConfigCache;
        this.eventPublisher = eventPublisher;
        this.buildOrchestrator = buildOrchestrator;
        this.mapper = mapper;
        this.podInfoQuery = podInfoQuery;
        this.clusterCapacityQuery = clusterCapacityQuery;
        this.prometheusQueryService = prometheusQueryService;
    }

    /** Called from the SSE reader — one frame at a time. {@code event} is the wire SSE event name. */
    public void handle(String event, String data) {
        try {
            switch (event) {
                case TunnelEventNames.APPLY_RESOURCE_BUNDLE ->
                    handleApplyBundle(mapper.readValue(data, ApplyResourceBundle.class));
                case TunnelEventNames.DELETE_RESOURCE -> handleDelete(mapper.readValue(data, DeleteResource.class));
                case TunnelEventNames.APPLY_NAMESPACE ->
                    handleApplyNamespace(mapper.readValue(data, ApplyNamespace.class));
                case TunnelEventNames.DELETE_NAMESPACE ->
                    handleDeleteNamespace(mapper.readValue(data, DeleteNamespace.class));
                case TunnelEventNames.NUDGE_IMAGE_SOURCE ->
                    handleNudgeImageSource(mapper.readValue(data, NudgeImageSource.class));
                case TunnelEventNames.CANCEL_BUILD -> handleCancelBuild(mapper.readValue(data, CancelBuild.class));
                case TunnelEventNames.RETRY_BUILD -> handleRetryBuild(mapper.readValue(data, RetryBuild.class));
                case TunnelEventNames.LIST_PODS -> handleListPods(mapper.readValue(data, ListPods.class));
                case TunnelEventNames.QUERY_CLUSTER_CAPACITY ->
                    handleQueryClusterCapacity(mapper.readValue(data, QueryClusterCapacity.class));
                case TunnelEventNames.QUERY_METRICS -> handleQueryMetrics(mapper.readValue(data, QueryMetrics.class));
                case TunnelEventNames.HELLO_ACK -> handleHelloAck(mapper.readValue(data, HelloAck.class));
                case TunnelEventNames.QUOTA_RBAC_CACHE_PUSH -> {
                    var push = mapper.readValue(data, QuotaRbacCachePush.class);
                    if (push.getSnapshot() != null) {
                        admissionCache.ingest(push.getRevision(), push.getSnapshot());
                    }
                }
                case TunnelEventNames.ADMIN_CONFIG_PUSH -> {
                    var push = mapper.readValue(data, AdminConfigPush.class);
                    if (push.getSnapshot() != null) {
                        adminConfigCache.ingest(push.getRevision(), push.getSnapshot());
                    }
                }
                case TunnelEventNames.KEEPALIVE -> {
                    // No-op; presence of the frame is the only information we need.
                }
                default -> log.debug("Unhandled tunnel event: {}", event);
            }
        } catch (Exception e) {
            // Catch-all: a single bad command must not tear the stream down.
            log.warn("Command handler threw: {}", e.getMessage(), e);
        }
    }

    private void handleHelloAck(HelloAck hello) {
        if (hello.getQuotaRbac() != null && hello.getQuotaRbac().getSnapshot() != null) {
            admissionCache.ingest(
                    hello.getQuotaRbac().getRevision(), hello.getQuotaRbac().getSnapshot());
        }
        if (hello.getAdminConfig() != null && hello.getAdminConfig().getSnapshot() != null) {
            adminConfigCache.ingest(
                    hello.getAdminConfig().getRevision(), hello.getAdminConfig().getSnapshot());
        }
    }

    private void handleApplyBundle(ApplyResourceBundle bundle) {
        String correlationId = bundle.getCorrelationId();
        ResourceCrd resource = bundle.getResource();
        ImageSourceCrd imageSource = bundle.getImageSource();
        if (resource == null && imageSource == null) {
            ack(correlationId, StatusEnum.INVALID_ARGUMENT, "bundle requires a resource or an imageSource");
            return;
        }
        if (resource != null) {
            stampEnvironmentSlugLabel(resource, bundle.getNamespace());
        }
        if (imageSource != null) {
            stampEnvironmentSlugLabel(imageSource, bundle.getNamespace());
        }
        try {
            // ImageSource-only edits (cross-cluster promotion broker, manual promote/rollback)
            // carry a null Resource and just SSA the ImageSource. The pair lives in the same
            // namespace so the existing OwnerReference (set when the Resource was first created)
            // remains valid; we don't need to touch it here.
            if (resource == null) {
                kubernetesClient
                        .resources(ImageSourceCrd.class)
                        .inNamespace(bundle.getNamespace())
                        .resource(imageSource)
                        .forceConflicts()
                        .serverSideApply();
                log.info(
                        "Applied ImageSource (no Resource update) {}/{}",
                        bundle.getNamespace(),
                        imageSource.getMetadata().getName());
                ack(correlationId, StatusEnum.OK, "");
                return;
            }
            // forceConflicts so the platform's apply takes ownership when another
            // field manager (e.g. an ad-hoc `kubectl patch`) previously claimed
            // the same field. The platform is the canonical source for spec; the
            // operator applies whatever it sends without re-litigating ownership.
            ResourceCrd applied = kubernetesClient
                    .resources(ResourceCrd.class)
                    .inNamespace(bundle.getNamespace())
                    .resource(resource)
                    .forceConflicts()
                    .serverSideApply();
            // Resource-only edits (stop/start/restart) carry a null imageSource — the operator
            // applies just the Resource and skips the ImageSource step. The bundle stays the
            // unit of dispatch even when only one half changes.
            if (imageSource == null) {
                log.info(
                        "Applied Resource (no ImageSource update) {}/{}",
                        bundle.getNamespace(),
                        resource.getMetadata().getName());
                ack(correlationId, StatusEnum.OK, "");
                return;
            }
            // SSA returns the materialised object including the freshly-assigned UID — use it to
            // wire the ImageSource's OwnerReference inline so the kubectl-apply auto-bind path
            // never has to fire on this pair.
            String resourceUid =
                    applied.getMetadata() != null ? applied.getMetadata().getUid() : null;
            if (resourceUid != null && !resourceUid.isBlank()) {
                imageSource
                        .getMetadata()
                        .setOwnerReferences(java.util.List.of(new OwnerReferenceBuilder()
                                .withApiVersion(applied.getApiVersion())
                                .withKind(applied.getKind())
                                .withName(applied.getMetadata().getName())
                                .withUid(resourceUid)
                                .withController(true)
                                .withBlockOwnerDeletion(true)
                                .build()));
            }
            kubernetesClient
                    .resources(ImageSourceCrd.class)
                    .inNamespace(bundle.getNamespace())
                    .resource(imageSource)
                    .forceConflicts()
                    .serverSideApply();
            log.info(
                    "Applied Resource+ImageSource bundle {}/{}",
                    bundle.getNamespace(),
                    resource.getMetadata().getName());
            ack(correlationId, StatusEnum.OK, "");
        } catch (Exception e) {
            log.warn("ApplyResourceBundle {} failed: {}", resource.getMetadata().getName(), e.getMessage());
            ack(correlationId, StatusEnum.INTERNAL_ERROR, e.getMessage());
        }
    }

    private void handleDelete(DeleteResource del) {
        String correlationId = del.getCorrelationId();
        try {
            kubernetesClient
                    .resources(ResourceCrd.class)
                    .inNamespace(del.getNamespace())
                    .withName(del.getResourceSlug())
                    .delete();
            log.info("Deleted Resource CR {}/{}", del.getNamespace(), del.getResourceSlug());
            // Notify the platform directly. JOSDK's cleanup() also emits the same event when
            // the finalizer is honoured, but if the CR is hard-deleted before JOSDK can add
            // its finalizer (apply-then-delete race), cleanup() never fires — and the cache
            // row would only disappear at the next successful full-sync. The platform's
            // deleteResourceSync is idempotent, so the double-emit is safe.
            try {
                var event = new ResourceDeletedBatch();
                event.getResourceSlugs().add(del.getResourceSlug());
                eventPublisher.emit(event);
            } catch (Exception e) {
                log.warn("Failed to emit immediate deletion event for {}: {}", del.getResourceSlug(), e.getMessage());
            }
            ack(correlationId, StatusEnum.OK, "");
        } catch (Exception e) {
            log.warn("DeleteResource {} failed: {}", del.getResourceSlug(), e.getMessage());
            ack(correlationId, StatusEnum.INTERNAL_ERROR, e.getMessage());
        }
    }

    private void handleApplyNamespace(ApplyNamespace cmd) {
        String correlationId = cmd.getCorrelationId();
        try {
            var ns = new NamespaceBuilder()
                    .withNewMetadata()
                    .withName(cmd.getNamespace())
                    .withLabels(Map.of(
                            Labels.MANAGED_BY_KEY,
                            Labels.MANAGED_BY_VALUE,
                            Labels.ENVIRONMENT_SLUG_KEY,
                            cmd.getEnvironmentSlug()))
                    .endMetadata()
                    .build();
            // Server-side apply is idempotent; re-sending the same command is a no-op.
            kubernetesClient.namespaces().resource(ns).forceConflicts().serverSideApply();
            log.info("Applied namespace {} for env {}", cmd.getNamespace(), cmd.getEnvironmentSlug());
            ack(correlationId, StatusEnum.OK, "");
        } catch (Exception e) {
            log.warn("ApplyNamespace {} failed: {}", cmd.getNamespace(), e.getMessage());
            ack(correlationId, StatusEnum.INTERNAL_ERROR, e.getMessage());
        }
    }

    private void handleDeleteNamespace(DeleteNamespace cmd) {
        String correlationId = cmd.getCorrelationId();
        try {
            kubernetesClient.namespaces().withName(cmd.getNamespace()).delete();
            log.info("Deleted namespace {}", cmd.getNamespace());
            ack(correlationId, StatusEnum.OK, "");
        } catch (KubernetesClientException e) {
            if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                log.info("Namespace {} already gone — treating delete as success", cmd.getNamespace());
                ack(correlationId, StatusEnum.OK, "");
                return;
            }
            log.warn("DeleteNamespace {} failed: {}", cmd.getNamespace(), e.getMessage());
            ack(correlationId, StatusEnum.INTERNAL_ERROR, e.getMessage());
        } catch (Exception e) {
            log.warn("DeleteNamespace {} failed: {}", cmd.getNamespace(), e.getMessage());
            ack(correlationId, StatusEnum.INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * Patches {@code status.lastWebhookAt} on the named ImageSource so the reconciler observes
     * the webhook arrival and triggers a fresh reconcile (which re-pulls HEAD itself — there
     * is no payload to consume).
     */
    private void handleNudgeImageSource(NudgeImageSource cmd) {
        String correlationId = cmd.getCorrelationId();
        try {
            var existing = kubernetesClient
                    .resources(ImageSourceCrd.class)
                    .inNamespace(cmd.getNamespace())
                    .withName(cmd.getName())
                    .get();
            if (existing == null) {
                ack(correlationId, StatusEnum.NOT_FOUND, "ImageSource not found");
                return;
            }
            var status = existing.getStatus() != null ? existing.getStatus() : new ImageSourceStatus();
            status.setLastWebhookAt(java.time.Instant.now());
            existing.setStatus(status);
            kubernetesClient
                    .resources(ImageSourceCrd.class)
                    .inNamespace(cmd.getNamespace())
                    .withName(cmd.getName())
                    .patchStatus(existing);
            log.info("Stamped lastWebhookAt on ImageSource {}/{}", cmd.getNamespace(), cmd.getName());
            ack(correlationId, StatusEnum.OK, "");
        } catch (Exception e) {
            log.warn("NudgeImageSource {}/{} failed: {}", cmd.getNamespace(), cmd.getName(), e.getMessage());
            ack(correlationId, StatusEnum.INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * Patch the named ImageSource's status: mark the matching {@code pendingBuild} (or
     * {@code queuedBuild}) as {@code CANCELED}, emit the lifecycle event, and delete the
     * in-flight build {@code Job}. The platform side has already verified the deployment was
     * in a cancellable phase ({@code Queued} / {@code Building}) and marked the audit row;
     * we trust that check and don't re-validate here.
     */
    private void handleCancelBuild(CancelBuild cmd) {
        String correlationId = cmd.getCorrelationId();
        try {
            var existing = kubernetesClient
                    .resources(ImageSourceCrd.class)
                    .inNamespace(cmd.getNamespace())
                    .withName(cmd.getImageSourceName())
                    .get();
            if (existing == null) {
                ack(correlationId, StatusEnum.NOT_FOUND, "ImageSource not found");
                return;
            }
            ImageSourceStatus status = existing.getStatus() != null ? existing.getStatus() : new ImageSourceStatus();
            boolean cancelled = buildOrchestrator.cancelBuild(existing, status, cmd.getDeploymentId());
            if (!cancelled) {
                // The build already terminated between the platform-side API call and the
                // command landing here. Treat as success: the platform's audit row already
                // reflects CANCELED, and there's nothing left to cancel on this side.
                log.info(
                        "CancelBuild {}/{} deploymentId={} found no in-flight build; treating as ok",
                        cmd.getNamespace(),
                        cmd.getImageSourceName(),
                        cmd.getDeploymentId());
                ack(correlationId, StatusEnum.OK, "");
                return;
            }
            existing.setStatus(status);
            kubernetesClient
                    .resources(ImageSourceCrd.class)
                    .inNamespace(cmd.getNamespace())
                    .withName(cmd.getImageSourceName())
                    .patchStatus(existing);
            log.info(
                    "Cancelled build on ImageSource {}/{} for deploymentId {}",
                    cmd.getNamespace(),
                    cmd.getImageSourceName(),
                    cmd.getDeploymentId());
            ack(correlationId, StatusEnum.OK, "");
        } catch (Exception e) {
            log.warn("CancelBuild {}/{} failed: {}", cmd.getNamespace(), cmd.getImageSourceName(), e.getMessage());
            ack(correlationId, StatusEnum.INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * Stage a retry. For git ImageSources, write a fresh {@code pendingBuild} (or
     * {@code queuedBuild}) entry carrying the platform-supplied {@code deploymentId} — the next
     * reconcile arms the Job. For image ImageSources, bump the sibling Resource's
     * {@code spec.restartGeneration} so the rollout half re-fires and {@code
     * ReleaseLifecycleEmitter} emits {@code ACTIVATING} for the new deployment id.
     */
    private void handleRetryBuild(RetryBuild cmd) {
        String correlationId = cmd.getCorrelationId();
        try {
            var existing = kubernetesClient
                    .resources(ImageSourceCrd.class)
                    .inNamespace(cmd.getNamespace())
                    .withName(cmd.getImageSourceName())
                    .get();
            if (existing == null) {
                ack(correlationId, StatusEnum.NOT_FOUND, "ImageSource not found");
                return;
            }
            ImageSourceSpec spec = existing.getSpec();
            ImageSourceType type = spec != null ? spec.getType() : null;
            if (type == ImageSourceType.IMAGE) {
                bumpResourceRestartGeneration(cmd.getNamespace(), cmd.getImageSourceName());
                log.info(
                        "Retry on image-type ImageSource {}/{}: bumped Resource restartGeneration",
                        cmd.getNamespace(),
                        cmd.getImageSourceName());
                ack(correlationId, StatusEnum.OK, "");
                return;
            }
            // Git + imageSource (promotion) retries go through the build-orchestrator path.
            // imageSource-type retries are unusual — promotion is normally just a digest pin and
            // doesn't run a build — but if the platform asked for one we honour the request.
            ImageSourceStatus status = existing.getStatus() != null ? existing.getStatus() : new ImageSourceStatus();
            boolean staged =
                    buildOrchestrator.requestRetry(existing, status, cmd.getDeploymentId(), cmd.getSourceCommit());
            if (!staged) {
                ack(correlationId, StatusEnum.INVALID_ARGUMENT, "retry requires a sourceCommit on git ImageSources");
                return;
            }
            existing.setStatus(status);
            kubernetesClient
                    .resources(ImageSourceCrd.class)
                    .inNamespace(cmd.getNamespace())
                    .withName(cmd.getImageSourceName())
                    .patchStatus(existing);
            log.info(
                    "Staged retry on ImageSource {}/{} for deploymentId {} commit {}",
                    cmd.getNamespace(),
                    cmd.getImageSourceName(),
                    cmd.getDeploymentId(),
                    cmd.getSourceCommit());
            ack(correlationId, StatusEnum.OK, "");
        } catch (Exception e) {
            log.warn("RetryBuild {}/{} failed: {}", cmd.getNamespace(), cmd.getImageSourceName(), e.getMessage());
            ack(correlationId, StatusEnum.INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * Bump {@code Resource.spec.restartGeneration} on the sibling Resource (same name + namespace
     * as the ImageSource). Mirrors what {@code ResourceLifecycleService.restart} does on the
     * platform side: any new value forces the Resource reconciler's re-roll path, which mints
     * an {@code ACTIVATING} BuildLifecycleEvent stamped with the next deployment id.
     */
    private void bumpResourceRestartGeneration(String namespace, String name) {
        try {
            kubernetesClient
                    .resources(ResourceCrd.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .edit(existing -> {
                        existing.getSpec().setRestartGeneration(System.currentTimeMillis());
                        return existing;
                    });
        } catch (Exception e) {
            log.warn("Failed to bump restartGeneration on Resource {}/{}: {}", namespace, name, e.getMessage());
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    /**
     * Resolve the env-slug label from the admission-snapshot cache when the platform didn't
     * supply it on the metadata. Keeps downstream code (admission webhook, reconciler) from
     * having to reconstruct the env from the namespace string.
     */
    private void stampEnvironmentSlugLabel(io.fabric8.kubernetes.api.model.HasMetadata cr, String namespace) {
        if (cr.getMetadata() == null) {
            return;
        }
        if (cr.getMetadata().getLabels() != null
                && cr.getMetadata().getLabels().containsKey(Labels.ENVIRONMENT_SLUG_KEY)) {
            return;
        }
        String fallbackEnvSlug =
                admissionCache.environmentSlugForNamespace(namespace).orElse("");
        if (fallbackEnvSlug.isBlank()) {
            return;
        }
        Map<String, String> labels = cr.getMetadata().getLabels();
        if (labels == null) {
            labels = new HashMap<>();
            cr.getMetadata().setLabels(labels);
        }
        labels.putIfAbsent(Labels.ENVIRONMENT_SLUG_KEY, fallbackEnvSlug);
    }

    private void handleListPods(ListPods cmd) {
        String correlationId = cmd.getCorrelationId();
        try {
            var entries = podInfoQuery.listPods(cmd.getNamespace(), cmd.getResourceSlug());
            var result = new ListPodsResult();
            result.setPods(entries);
            ackWithPayload(correlationId, result);
        } catch (Exception e) {
            log.warn("ListPods {}/{} failed: {}", cmd.getNamespace(), cmd.getResourceSlug(), e.getMessage());
            ack(correlationId, StatusEnum.INTERNAL_ERROR, e.getMessage());
        }
    }

    private void handleQueryClusterCapacity(QueryClusterCapacity cmd) {
        String correlationId = cmd.getCorrelationId();
        try {
            var result = clusterCapacityQuery.compute();
            ackWithPayload(correlationId, result);
        } catch (Exception e) {
            log.warn("QueryClusterCapacity failed: {}", e.getMessage());
            ack(correlationId, StatusEnum.INTERNAL_ERROR, e.getMessage());
        }
    }

    private void handleQueryMetrics(QueryMetrics cmd) {
        String correlationId = cmd.getCorrelationId();
        try {
            var result = prometheusQueryService.query(
                    cmd.getNamespace(),
                    cmd.getResourceSlug(),
                    cmd.getKind(),
                    cmd.getStartEpochSeconds(),
                    cmd.getEndEpochSeconds(),
                    cmd.getStepSeconds(),
                    cmd.getPod());
            ackWithPayload(correlationId, result);
        } catch (Exception e) {
            log.warn("QueryMetrics {}/{} failed: {}", cmd.getNamespace(), cmd.getResourceSlug(), e.getMessage());
            ack(correlationId, StatusEnum.INTERNAL_ERROR, e.getMessage());
        }
    }

    private void ack(String correlationId, StatusEnum status, String message) {
        var req = new AckCommandRequest();
        var response = new CommandResponse();
        response.setStatus(status);
        response.setMessage(message != null ? message : "");
        req.setResponse(response);
        tunnelApiClient.ackCommand(correlationId, req);
    }

    /** Action commands ack with status + message only; read commands tack a typed payload on. */
    private void ackWithPayload(String correlationId, Object payload) {
        var req = new AckCommandRequest();
        var response = new CommandResponse();
        response.setStatus(StatusEnum.OK);
        response.setMessage("");
        if (payload instanceof ListPodsResult listPodsResult) {
            response.setPayload(listPodsResult);
        } else if (payload instanceof ClusterCapacityResult capacityResult) {
            response.setPayload(capacityResult);
        } else if (payload instanceof MetricsResult metricsResult) {
            response.setPayload(metricsResult);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported response payload type: " + payload.getClass().getName());
        }
        req.setResponse(response);
        tunnelApiClient.ackCommand(correlationId, req);
    }
}

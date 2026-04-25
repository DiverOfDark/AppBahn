package eu.appbahn.operator.tunnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.operator.tunnel.client.model.AckCommandRequest;
import eu.appbahn.operator.tunnel.client.model.AdminConfigPush;
import eu.appbahn.operator.tunnel.client.model.ApplyNamespace;
import eu.appbahn.operator.tunnel.client.model.ApplyResource;
import eu.appbahn.operator.tunnel.client.model.CommandResponse;
import eu.appbahn.operator.tunnel.client.model.CommandResponse.StatusEnum;
import eu.appbahn.operator.tunnel.client.model.DeleteNamespace;
import eu.appbahn.operator.tunnel.client.model.DeleteResource;
import eu.appbahn.operator.tunnel.client.model.HelloAck;
import eu.appbahn.operator.tunnel.client.model.QuotaRbacCachePush;
import eu.appbahn.operator.tunnel.client.model.ResourceDeletedBatch;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
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
    private final ObjectMapper mapper;

    public PlatformCommandHandler(
            KubernetesClient kubernetesClient,
            TunnelApiClient tunnelApiClient,
            AdmissionSnapshotCache admissionCache,
            AdminConfigCache adminConfigCache,
            OperatorEventPublisher eventPublisher,
            ObjectMapper mapper) {
        this.kubernetesClient = kubernetesClient;
        this.tunnelApiClient = tunnelApiClient;
        this.admissionCache = admissionCache;
        this.adminConfigCache = adminConfigCache;
        this.eventPublisher = eventPublisher;
        this.mapper = mapper;
    }

    /** Called from the SSE reader — one frame at a time. {@code event} is the wire SSE event name. */
    public void handle(String event, String data) {
        try {
            switch (event) {
                case TunnelEventNames.APPLY_RESOURCE -> handleApply(mapper.readValue(data, ApplyResource.class));
                case TunnelEventNames.DELETE_RESOURCE -> handleDelete(mapper.readValue(data, DeleteResource.class));
                case TunnelEventNames.APPLY_NAMESPACE ->
                    handleApplyNamespace(mapper.readValue(data, ApplyNamespace.class));
                case TunnelEventNames.DELETE_NAMESPACE ->
                    handleDeleteNamespace(mapper.readValue(data, DeleteNamespace.class));
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

    private void handleApply(ApplyResource apply) {
        String correlationId = apply.getCorrelationId();
        ResourceCrd crd = buildCrd(apply);
        String slug = crd.getMetadata() != null && crd.getMetadata().getName() != null
                ? crd.getMetadata().getName()
                : "";
        try {
            kubernetesClient
                    .resources(ResourceCrd.class)
                    .inNamespace(apply.getNamespace())
                    .resource(crd)
                    .serverSideApply();
            log.info("Applied Resource CR {}/{}", apply.getNamespace(), slug);
            ack(correlationId, StatusEnum.OK, "");
        } catch (Exception e) {
            log.warn("ApplyResource {} failed: {}", slug, e.getMessage());
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
            kubernetesClient.namespaces().resource(ns).serverSideApply();
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
     * Stamp the env-slug label onto the metadata if the platform didn't supply it — we resolve
     * it from the admission-snapshot cache instead. Keeps downstream code (admission webhook,
     * reconciler) from having to reconstruct the env from the namespace.
     */
    private ResourceCrd buildCrd(ApplyResource apply) {
        ResourceCrd crd = apply.getResource();
        if (crd == null) {
            throw new IllegalStateException(
                    "ApplyResource has no resource payload for correlation_id " + apply.getCorrelationId());
        }
        if (crd.getMetadata() != null
                && (crd.getMetadata().getLabels() == null
                        || !crd.getMetadata().getLabels().containsKey(Labels.ENVIRONMENT_SLUG_KEY))) {
            String fallbackEnvSlug = admissionCache
                    .environmentSlugForNamespace(apply.getNamespace())
                    .orElse("");
            if (!fallbackEnvSlug.isBlank()) {
                Map<String, String> labels = crd.getMetadata().getLabels();
                if (labels == null) {
                    labels = new HashMap<>();
                    crd.getMetadata().setLabels(labels);
                }
                labels.putIfAbsent(Labels.ENVIRONMENT_SLUG_KEY, fallbackEnvSlug);
            }
        }
        return crd;
    }

    private void ack(String correlationId, StatusEnum status, String message) {
        var req = new AckCommandRequest();
        var response = new CommandResponse();
        response.setStatus(status);
        response.setMessage(message != null ? message : "");
        req.setResponse(response);
        tunnelApiClient.ackCommand(correlationId, req);
    }
}

package eu.appbahn.operator.tunnel;

import com.google.protobuf.Empty;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.tunnel.ResourceWireMapper;
import eu.appbahn.tunnel.v1.AckCommandRequest;
import eu.appbahn.tunnel.v1.ApplyNamespace;
import eu.appbahn.tunnel.v1.ApplyResource;
import eu.appbahn.tunnel.v1.CommandResponse;
import eu.appbahn.tunnel.v1.DeleteNamespace;
import eu.appbahn.tunnel.v1.DeleteResource;
import eu.appbahn.tunnel.v1.OperatorEvent;
import eu.appbahn.tunnel.v1.PlatformMessage;
import eu.appbahn.tunnel.v1.ResourceDeletedBatch;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dispatches {@link PlatformMessage}s received from {@code SubscribeCommands} into
 * concrete actions on this cluster. Each command's outcome is reported back via a
 * unary {@code AckCommand} RPC carrying the original {@code correlation_id}.
 */
@Service
public class PlatformCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(PlatformCommandHandler.class);

    private final KubernetesClient kubernetesClient;
    private final OperatorTunnelClient tunnelClient;
    private final AdmissionSnapshotCache admissionCache;
    private final AdminConfigCache adminConfigCache;
    private final OperatorEventPublisher eventPublisher;

    public PlatformCommandHandler(
            KubernetesClient kubernetesClient,
            OperatorTunnelClient tunnelClient,
            AdmissionSnapshotCache admissionCache,
            AdminConfigCache adminConfigCache,
            OperatorEventPublisher eventPublisher) {
        this.kubernetesClient = kubernetesClient;
        this.tunnelClient = tunnelClient;
        this.admissionCache = admissionCache;
        this.adminConfigCache = adminConfigCache;
        this.eventPublisher = eventPublisher;
    }

    public void handle(PlatformMessage message) {
        try {
            switch (message.getMessageCase()) {
                case APPLY_RESOURCE -> handleApply(message.getApplyResource());
                case DELETE_RESOURCE -> handleDelete(message.getDeleteResource());
                case APPLY_NAMESPACE -> handleApplyNamespace(message.getApplyNamespace());
                case DELETE_NAMESPACE -> handleDeleteNamespace(message.getDeleteNamespace());
                case HELLO_ACK -> {
                    // The initial snapshots are piggy-backed on HelloAck so the admission webhook
                    // and admin-config consumers are never in a "no data" state after a successful
                    // subscription.
                    var hello = message.getHelloAck();
                    if (hello.hasQuotaRbac()) {
                        admissionCache.ingest(
                                hello.getQuotaRbac().getRevision(),
                                hello.getQuotaRbac().getSnapshot());
                    }
                    if (hello.hasAdminConfig()) {
                        adminConfigCache.ingest(
                                hello.getAdminConfig().getRevision(),
                                hello.getAdminConfig().getSnapshot());
                    }
                }
                case QUOTA_RBAC_CACHE_PUSH -> {
                    var push = message.getQuotaRbacCachePush();
                    admissionCache.ingest(push.getRevision(), push.getSnapshot());
                }
                case ADMIN_CONFIG_PUSH -> {
                    var push = message.getAdminConfigPush();
                    adminConfigCache.ingest(push.getRevision(), push.getSnapshot());
                }
                case MESSAGE_NOT_SET -> {
                    log.debug("Unhandled PlatformMessage case: {}", message.getMessageCase());
                }
            }
        } catch (Exception e) {
            // Catch-all: a single bad command must not tear the stream down.
            log.warn("Command handler threw: {}", e.getMessage(), e);
        }
    }

    private void handleApply(ApplyResource apply) throws IOException {
        String correlationId = apply.getCorrelationId();
        String slug = apply.getResource().getSlug();
        try {
            ResourceCrd crd = buildCrd(apply);
            kubernetesClient
                    .resources(ResourceCrd.class)
                    .inNamespace(apply.getNamespace())
                    .resource(crd)
                    .serverSideApply();
            log.info("Applied Resource CR {}/{}", apply.getNamespace(), slug);
            ack(correlationId, CommandResponse.Status.STATUS_OK, "");
        } catch (Exception e) {
            log.warn("ApplyResource {} failed: {}", slug, e.getMessage());
            ack(correlationId, CommandResponse.Status.STATUS_INTERNAL_ERROR, e.getMessage());
        }
    }

    private void handleDelete(DeleteResource del) throws IOException {
        String correlationId = del.getCorrelationId();
        try {
            kubernetesClient
                    .resources(ResourceCrd.class)
                    .inNamespace(del.getNamespace())
                    .withName(del.getResourceSlug())
                    .delete();
            log.info("Deleted Resource CR {}/{}", del.getNamespace(), del.getResourceSlug());
            // Notify the platform directly. JOSDK's cleanup() also emits the same event when the
            // finalizer is honoured, but if the CR is hard-deleted before JOSDK can add its
            // finalizer (apply-then-delete race), cleanup() never fires — and the cache row
            // would only disappear at the next successful full-sync. The platform's
            // deleteResourceSync is idempotent, so the double-emit is safe.
            try {
                eventPublisher.emit(OperatorEvent.newBuilder()
                        .setResourceDeletedBatch(
                                ResourceDeletedBatch.newBuilder().addResourceSlugs(del.getResourceSlug()))
                        .build());
            } catch (Exception e) {
                log.warn("Failed to emit immediate deletion event for {}: {}", del.getResourceSlug(), e.getMessage());
            }
            ack(correlationId, CommandResponse.Status.STATUS_OK, "");
        } catch (Exception e) {
            log.warn("DeleteResource {} failed: {}", del.getResourceSlug(), e.getMessage());
            ack(correlationId, CommandResponse.Status.STATUS_INTERNAL_ERROR, e.getMessage());
        }
    }

    private void handleApplyNamespace(ApplyNamespace cmd) throws IOException {
        String correlationId = cmd.getCorrelationId();
        try {
            var ns = new NamespaceBuilder()
                    .withNewMetadata()
                    .withName(cmd.getNamespace())
                    .withLabels(java.util.Map.of(
                            Labels.MANAGED_BY_KEY,
                            Labels.MANAGED_BY_VALUE,
                            Labels.ENVIRONMENT_SLUG_KEY,
                            cmd.getEnvironmentSlug()))
                    .endMetadata()
                    .build();
            // Server-side apply is idempotent; re-sending the same command is a no-op.
            kubernetesClient.namespaces().resource(ns).serverSideApply();
            log.info("Applied namespace {} for env {}", cmd.getNamespace(), cmd.getEnvironmentSlug());
            ack(correlationId, CommandResponse.Status.STATUS_OK, "");
        } catch (Exception e) {
            log.warn("ApplyNamespace {} failed: {}", cmd.getNamespace(), e.getMessage());
            ack(correlationId, CommandResponse.Status.STATUS_INTERNAL_ERROR, e.getMessage());
        }
    }

    private void handleDeleteNamespace(DeleteNamespace cmd) throws IOException {
        String correlationId = cmd.getCorrelationId();
        try {
            kubernetesClient.namespaces().withName(cmd.getNamespace()).delete();
            log.info("Deleted namespace {}", cmd.getNamespace());
            ack(correlationId, CommandResponse.Status.STATUS_OK, "");
        } catch (KubernetesClientException e) {
            if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                log.info("Namespace {} already gone — treating delete as success", cmd.getNamespace());
                ack(correlationId, CommandResponse.Status.STATUS_OK, "");
                return;
            }
            log.warn("DeleteNamespace {} failed: {}", cmd.getNamespace(), e.getMessage());
            ack(correlationId, CommandResponse.Status.STATUS_INTERNAL_ERROR, e.getMessage());
        } catch (Exception e) {
            log.warn("DeleteNamespace {} failed: {}", cmd.getNamespace(), e.getMessage());
            ack(correlationId, CommandResponse.Status.STATUS_INTERNAL_ERROR, e.getMessage());
        }
    }

    private ResourceCrd buildCrd(ApplyResource apply) {
        // If the platform didn't supply an env slug on the wire, fall back to whatever the
        // admission-snapshot cache has for this namespace.
        String fallbackEnvSlug =
                admissionCache.environmentSlugForNamespace(apply.getNamespace()).orElse("");
        return ResourceWireMapper.toCrd(apply, fallbackEnvSlug);
    }

    private void ack(String correlationId, CommandResponse.Status status, String message) throws IOException {
        var req = AckCommandRequest.newBuilder()
                .setCorrelationId(correlationId)
                .setResponse(CommandResponse.newBuilder().setStatus(status).setMessage(message != null ? message : ""))
                .build();
        tunnelClient.unary("AckCommand", req, Empty.newBuilder());
    }
}

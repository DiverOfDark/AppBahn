package eu.appbahn.operator.tunnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.operator.tunnel.client.model.AckCommandRequest;
import eu.appbahn.operator.tunnel.client.model.AdminConfigPush;
import eu.appbahn.operator.tunnel.client.model.ApplyNamespace;
import eu.appbahn.operator.tunnel.client.model.ApplyResourceBundle;
import eu.appbahn.operator.tunnel.client.model.CommandResponse;
import eu.appbahn.operator.tunnel.client.model.CommandResponse.StatusEnum;
import eu.appbahn.operator.tunnel.client.model.DeleteNamespace;
import eu.appbahn.operator.tunnel.client.model.DeleteResource;
import eu.appbahn.operator.tunnel.client.model.HelloAck;
import eu.appbahn.operator.tunnel.client.model.QuotaRbacCachePush;
import eu.appbahn.operator.tunnel.client.model.ResourceDeletedBatch;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
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
                case TunnelEventNames.APPLY_RESOURCE_BUNDLE ->
                    handleApplyBundle(mapper.readValue(data, ApplyResourceBundle.class));
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
                        .serverSideApply();
                log.info(
                        "Applied ImageSource (no Resource update) {}/{}",
                        bundle.getNamespace(),
                        imageSource.getMetadata().getName());
                ack(correlationId, StatusEnum.OK, "");
                return;
            }
            ResourceCrd applied = kubernetesClient
                    .resources(ResourceCrd.class)
                    .inNamespace(bundle.getNamespace())
                    .resource(resource)
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

    private void ack(String correlationId, StatusEnum status, String message) {
        var req = new AckCommandRequest();
        var response = new CommandResponse();
        response.setStatus(status);
        response.setMessage(message != null ? message : "");
        req.setResponse(response);
        tunnelApiClient.ackCommand(correlationId, req);
    }
}

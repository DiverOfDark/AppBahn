package eu.appbahn.operator.reconciler;

import eu.appbahn.operator.client.model.ResourceSyncRequest;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourceCrd;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;

/** Shared builder to convert a {@link ResourceCrd} to a {@link ResourceSyncRequest}. */
final class ResourceSyncRequestBuilder {

    private ResourceSyncRequestBuilder() {}

    static ResourceSyncRequest fromCrd(ResourceCrd crd, String clusterName) {
        var request = new ResourceSyncRequest();
        request.setSlug(crd.getMetadata().getName());
        request.setName(
                crd.getSpec().getName() != null
                        ? crd.getSpec().getName()
                        : crd.getMetadata().getName());
        request.setType(crd.getSpec().getType() != null ? crd.getSpec().getType() : Labels.RESOURCE_TYPE_DEPLOYMENT);

        Map<String, String> labels =
                crd.getMetadata().getLabels() != null ? crd.getMetadata().getLabels() : Collections.emptyMap();
        request.setEnvironmentSlug(labels.getOrDefault(Labels.ENVIRONMENT_SLUG_KEY, ""));
        request.setClusterName(clusterName);

        request.setConfig(crd.getSpec().getConfig() != null ? crd.getSpec().getConfig() : new ResourceConfig());

        if (crd.getSpec().getLinks() != null && !crd.getSpec().getLinks().isEmpty()) {
            request.setLinks(crd.getSpec().getLinks());
        }

        var status = crd.getStatus();
        request.setStatus(
                status != null && status.getPhase() != null
                        ? ResourceSyncRequest.StatusEnum.valueOf(
                                status.getPhase().name())
                        : ResourceSyncRequest.StatusEnum.PENDING);

        if (status != null) {
            request.setStatusDetail(status);
        }

        if (crd.getMetadata().getCreationTimestamp() != null) {
            request.setCreatedAt(OffsetDateTime.parse(crd.getMetadata().getCreationTimestamp()));
        }

        return request;
    }
}

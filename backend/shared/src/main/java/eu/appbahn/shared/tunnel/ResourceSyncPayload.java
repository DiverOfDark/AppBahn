package eu.appbahn.shared.tunnel;

import com.fasterxml.jackson.annotation.JsonInclude;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceSpec;
import eu.appbahn.shared.crd.ResourceStatus;
import java.time.Instant;
import java.util.List;

/**
 * Operator→platform resource-sync payload, carried inside a
 * {@code OperatorEvent.ResourceSyncBatch}'s {@code ResourceSyncItem}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceSyncPayload(
        String slug,
        String environmentSlug,
        String clusterName,
        String name,
        String type,
        ResourceConfig config,
        List<ResourceSpec.ResourceLink> links,
        ResourcePhase status,
        ResourceStatus statusDetail,
        Instant createdAt,
        Long generation,
        String resourceVersion) {}

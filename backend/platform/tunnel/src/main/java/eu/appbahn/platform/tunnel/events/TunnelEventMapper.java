package eu.appbahn.platform.tunnel.events;

import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceSpec;
import eu.appbahn.shared.crd.ResourceStatus;
import eu.appbahn.shared.tunnel.ProtoCrdMapper;
import eu.appbahn.shared.tunnel.ResourceSyncPayload;
import eu.appbahn.tunnel.v1.ResourceSyncItem;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts a wire-level {@link ResourceSyncItem} into the internal {@link ResourceSyncPayload}. */
@Component
public class TunnelEventMapper {

    public ResourceSyncPayload toPayload(ResourceSyncItem item) {
        return toPayload(item, null);
    }

    /** {@code clusterName} is supplied by the caller (PushEvents request envelope). */
    public ResourceSyncPayload toPayload(ResourceSyncItem item, String clusterName) {
        var resource = item.getResource();
        ResourceConfig config = resource.hasConfig() ? ProtoCrdMapper.fromProto(resource.getConfig()) : null;
        List<ResourceSpec.ResourceLink> links = ProtoCrdMapper.linksFromProto(resource.getLinksList());
        ResourceStatus statusDetail =
                resource.hasStatusDetail() ? ProtoCrdMapper.fromProto(resource.getStatusDetail()) : null;
        ResourcePhase phase =
                resource.getStatus().isEmpty() ? ResourcePhase.PENDING : ResourcePhase.valueOf(resource.getStatus());
        Instant createdAt = parseInstant(resource.getCreatedAt());

        return new ResourceSyncPayload(
                resource.getSlug(),
                resource.getEnvironmentSlug(),
                clusterName,
                resource.getName(),
                resource.getType(),
                config,
                links,
                phase,
                statusDetail,
                createdAt,
                item.getGeneration() > 0 ? item.getGeneration() : null,
                item.getResourceVersion().isEmpty() ? null : item.getResourceVersion());
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return java.time.OffsetDateTime.parse(iso).toInstant();
        } catch (java.time.format.DateTimeParseException ignored) {
            return null;
        }
    }
}

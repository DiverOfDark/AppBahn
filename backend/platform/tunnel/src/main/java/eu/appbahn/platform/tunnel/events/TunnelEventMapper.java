package eu.appbahn.platform.tunnel.events;

import eu.appbahn.platform.api.tunnel.ResourceSyncItem;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.tunnel.ResourceSyncPayload;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

/** Converts a wire-level {@link ResourceSyncItem} into the internal {@link ResourceSyncPayload}. */
@Component
public class TunnelEventMapper {

    public ResourceSyncPayload toPayload(ResourceSyncItem item) {
        return toPayload(item, null);
    }

    /** {@code clusterName} is supplied by the caller (PushEvents request envelope). */
    public ResourceSyncPayload toPayload(ResourceSyncItem item, String clusterName) {
        ResourceCrd crd = item.getResource();
        if (crd == null) {
            return new ResourceSyncPayload(
                    "",
                    "",
                    clusterName,
                    "",
                    Labels.RESOURCE_TYPE_DEPLOYMENT,
                    null,
                    null,
                    ResourcePhase.PENDING,
                    null,
                    null,
                    item.getGeneration() > 0 ? item.getGeneration() : null,
                    emptyToNull(item.getResourceVersion()));
        }
        var spec = crd.getSpec();
        var meta = crd.getMetadata();
        var status = crd.getStatus();

        String slug = meta != null && meta.getName() != null ? meta.getName() : "";
        String envSlug = meta != null && meta.getLabels() != null
                ? meta.getLabels().getOrDefault(Labels.ENVIRONMENT_SLUG_KEY, "")
                : "";
        String name = spec != null && spec.getName() != null ? spec.getName() : slug;
        String type = spec != null && spec.getType() != null ? spec.getType() : Labels.RESOURCE_TYPE_DEPLOYMENT;
        ResourcePhase phase = status != null && status.getPhase() != null ? status.getPhase() : ResourcePhase.PENDING;
        Instant createdAt = parseCreationTimestamp(meta != null ? meta.getCreationTimestamp() : null);

        return new ResourceSyncPayload(
                slug,
                envSlug,
                clusterName,
                name,
                type,
                spec != null ? spec.getConfig() : null,
                spec != null ? spec.getLinks() : null,
                phase,
                status,
                createdAt,
                item.getGeneration() > 0 ? item.getGeneration() : null,
                emptyToNull(item.getResourceVersion()));
    }

    private static Instant parseCreationTimestamp(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(iso).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}

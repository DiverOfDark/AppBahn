package eu.appbahn.platform.tunnel.events;

import eu.appbahn.platform.api.tunnel.ImageSourceSyncItem;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.tunnel.ImageSourceSyncPayload;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

/** Converts a wire-level {@link ImageSourceSyncItem} into the internal {@link ImageSourceSyncPayload}. */
@Component
public class TunnelImageSourceMapper {

    public ImageSourceSyncPayload toPayload(ImageSourceSyncItem item, String clusterName) {
        ImageSourceCrd crd = item.getImageSource();
        if (crd == null) {
            return new ImageSourceSyncPayload(
                    "",
                    "",
                    clusterName,
                    null,
                    null,
                    null,
                    item.getGeneration() > 0 ? item.getGeneration() : null,
                    emptyToNull(item.getResourceVersion()));
        }
        var meta = crd.getMetadata();
        String slug = meta != null && meta.getName() != null ? meta.getName() : "";
        String envSlug = meta != null && meta.getLabels() != null
                ? meta.getLabels().getOrDefault(Labels.ENVIRONMENT_SLUG_KEY, "")
                : "";
        Instant createdAt = parseCreationTimestamp(meta != null ? meta.getCreationTimestamp() : null);
        return new ImageSourceSyncPayload(
                slug,
                envSlug,
                clusterName,
                crd.getSpec(),
                crd.getStatus(),
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

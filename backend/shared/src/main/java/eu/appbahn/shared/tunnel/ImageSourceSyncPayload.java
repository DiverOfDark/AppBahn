package eu.appbahn.shared.tunnel;

import com.fasterxml.jackson.annotation.JsonInclude;
import eu.appbahn.shared.crd.imagesource.ImageSourceSpec;
import eu.appbahn.shared.crd.imagesource.ImageSourceStatus;
import java.time.Instant;

/**
 * Operator→platform ImageSource-sync payload, carried inside an
 * {@code OperatorEvent.ImageSourceSyncBatch}'s {@code ImageSourceSyncItem}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImageSourceSyncPayload(
        String slug,
        String environmentSlug,
        String clusterName,
        ImageSourceSpec spec,
        ImageSourceStatus status,
        Instant createdAt,
        Long generation,
        String resourceVersion) {}

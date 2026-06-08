package eu.appbahn.platform.api.resource;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Data payload of a {@code log} frame on the {@code GET /resources/{slug}/logs/stream} SSE stream:
 * a single container log line tailed from the cluster's log provider.
 */
@Data
public class LogStreamLogFrame {

    @Nullable
    private OffsetDateTime timestamp;

    @Nullable
    private String message;

    @Nullable
    private String pod;

    @Nullable
    private String container;
}

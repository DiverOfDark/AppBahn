package eu.appbahn.platform.api.stats;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Bulk-rollup counters for a single workspace, returned by {@code GET /workspaces/stats}.
 * Every counter is computed by a single aggregate SQL query — the controller never
 * loads entity lists to count them.
 */
@Data
public class WorkspaceStats {

    @Nullable
    private String slug;

    private long projectCount;

    private long resourceCount;

    private long clusterCount;

    private long memberCount;

    /** Timestamp of the most recent audit-log entry for this workspace, or null if none. */
    @Valid
    @Nullable
    private OffsetDateTime lastEventAt;
}

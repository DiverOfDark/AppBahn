package eu.appbahn.platform.resource.stats;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@link StatsRepository#workspaceRollups}: per-workspace counters with the
 * timestamp of the most recent audit-log entry. Empty workspaces still produce a row
 * (zero counts, null {@code lastEventAt}).
 */
public record WorkspaceRollupRow(
        UUID workspaceId,
        String slug,
        long projectCount,
        long resourceCount,
        long clusterCount,
        long memberCount,
        Instant lastEventAt) {}

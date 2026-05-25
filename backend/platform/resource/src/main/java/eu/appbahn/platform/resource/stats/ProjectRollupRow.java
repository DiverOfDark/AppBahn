package eu.appbahn.platform.resource.stats;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@link StatsRepository#projectRollups}: per-project counters and the inputs
 * needed to derive uptime percentage. {@code readyCount}/{@code totalCount} carry the
 * raw ratio so the service decides the empty-project policy.
 */
public record ProjectRollupRow(
        UUID projectId,
        String slug,
        long services,
        long deploys7d,
        long readyCount,
        long totalCount,
        Instant lastDeployAt) {}

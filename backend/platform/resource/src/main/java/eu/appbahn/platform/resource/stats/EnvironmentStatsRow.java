package eu.appbahn.platform.resource.stats;

import java.util.UUID;

/**
 * One row of {@link StatsRepository#environmentStatsForProject}: env identity, worst-rank
 * status, plus three positionally-correlated arrays for CPU/memory/replicas. Index {@code i}
 * across {@code cpuQuantities}, {@code memoryQuantities} and {@code replicas} refers to the
 * same resource — the SQL {@code FILTER (WHERE rc.slug IS NOT NULL)} clauses keep them in
 * lockstep. All three are non-null but may be empty for environments with zero resources.
 *
 * <p>CPU and memory arrive as raw {@link io.fabric8.kubernetes.api.model.Quantity} strings
 * (e.g. {@code "250m"}, {@code "128Mi"}); {@link StatsService} folds them via Quantity math
 * rather than SQL.
 */
public record EnvironmentStatsRow(
        UUID envId,
        String slug,
        String status,
        String[] cpuQuantities,
        String[] memoryQuantities,
        Integer[] replicas) {}

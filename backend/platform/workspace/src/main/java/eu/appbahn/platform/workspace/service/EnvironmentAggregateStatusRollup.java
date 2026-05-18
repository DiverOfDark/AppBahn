package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.api.EnvironmentAggregateStatus;
import eu.appbahn.shared.crd.ResourcePhase;
import java.util.Map;

/**
 * Rolls up the per-status resource counts of a single environment into one
 * {@link EnvironmentAggregateStatus}. Worst phase wins; an env with zero resources or only
 * unrecognised phase strings rolls up to {@link EnvironmentAggregateStatus#UNKNOWN}. The same
 * mapping is used by the native SQL aggregation in {@code ResourceCacheAggregateRepository} —
 * keep them in sync.
 *
 * <p>Phase → bucket mapping:
 * <ul>
 *   <li>{@link ResourcePhase#ERROR} → {@link EnvironmentAggregateStatus#FAILED}
 *   <li>{@link ResourcePhase#DEGRADED}, {@link ResourcePhase#RESTARTING}
 *       → {@link EnvironmentAggregateStatus#DEGRADED}
 *   <li>{@link ResourcePhase#PENDING} → {@link EnvironmentAggregateStatus#PENDING}
 *   <li>{@link ResourcePhase#READY}, {@link ResourcePhase#STOPPED}
 *       → {@link EnvironmentAggregateStatus#HEALTHY} (STOPPED is a user-intended state)
 * </ul>
 */
public final class EnvironmentAggregateStatusRollup {

    private EnvironmentAggregateStatusRollup() {}

    /**
     * Combines per-phase counts into an aggregate. Null or missing entries are treated as zero.
     */
    public static EnvironmentAggregateStatus rollup(Map<ResourcePhase, Long> phaseCounts) {
        if (phaseCounts == null || phaseCounts.isEmpty()) {
            return EnvironmentAggregateStatus.UNKNOWN;
        }
        long total = 0;
        for (Long count : phaseCounts.values()) {
            if (count != null) {
                total += count;
            }
        }
        if (total == 0) {
            return EnvironmentAggregateStatus.UNKNOWN;
        }

        if (nonZero(phaseCounts, ResourcePhase.ERROR)) {
            return EnvironmentAggregateStatus.FAILED;
        }
        if (nonZero(phaseCounts, ResourcePhase.DEGRADED) || nonZero(phaseCounts, ResourcePhase.RESTARTING)) {
            return EnvironmentAggregateStatus.DEGRADED;
        }
        if (nonZero(phaseCounts, ResourcePhase.PENDING)) {
            return EnvironmentAggregateStatus.PENDING;
        }
        if (nonZero(phaseCounts, ResourcePhase.READY) || nonZero(phaseCounts, ResourcePhase.STOPPED)) {
            return EnvironmentAggregateStatus.HEALTHY;
        }
        return EnvironmentAggregateStatus.UNKNOWN;
    }

    private static boolean nonZero(Map<ResourcePhase, Long> counts, ResourcePhase phase) {
        Long c = counts.get(phase);
        return c != null && c > 0;
    }
}

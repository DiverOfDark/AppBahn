package eu.appbahn.platform.api.resource;

import jakarta.validation.Valid;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Aggregated deployment counts for a Resource over a sliding wall-clock window. Returned by
 * {@code GET /resources/{slug}/deployments/stats}.
 */
@Data
public class DeploymentStats {

    /** Sliding window length in days the {@link #buckets} cover. */
    @Nullable
    private Integer windowDays;

    /**
     * One entry per UTC calendar day inside the window, oldest first. A day with no deployments
     * is still present with zero counts so the frontend histogram has a stable bar count.
     */
    @Valid
    @Nullable
    private List<DeploymentStatsBucket> buckets;

    /** Aggregate counts across the full window. */
    @Valid
    @Nullable
    private DeploymentStatsTotals totals;
}

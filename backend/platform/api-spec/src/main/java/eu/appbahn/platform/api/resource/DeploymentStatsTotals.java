package eu.appbahn.platform.api.resource;

import lombok.Data;
import org.springframework.lang.Nullable;

/** Window-wide deployment counters. {@code successRate} is the headline number on the Deploys tab. */
@Data
public class DeploymentStatsTotals {

    /** Total deployments inside the window. */
    @Nullable
    private Integer total;

    /** Deployments that reached {@code ACTIVE} or {@code BUILT}. */
    @Nullable
    private Integer success;

    /** Deployments that reached {@code FAILED} or {@code CANCELED}. */
    @Nullable
    private Integer failure;

    /** Deployments whose {@code triggered_by} is {@code ROLLBACK}. */
    @Nullable
    private Integer rollback;

    /**
     * Percentage of {@code total} that succeeded, in {@code [0, 100]} with two decimal places.
     * {@code null} when {@code total == 0}.
     */
    @Nullable
    private Double successRate;
}

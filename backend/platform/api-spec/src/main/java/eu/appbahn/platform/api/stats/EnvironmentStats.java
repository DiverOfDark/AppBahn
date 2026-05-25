package eu.appbahn.platform.api.stats;

import eu.appbahn.shared.crd.ResourcePhase;
import io.fabric8.kubernetes.api.model.Quantity;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Bulk-rollup counters for a single environment, returned by {@code GET /environments/stats}.
 * {@code aggregateStatus} is the worst {@link ResourcePhase} across the environment's
 * resources; {@code configuredCpu}/{@code configuredMemory} are the sum of every resource's
 * configured request multiplied by its effective replica count (worst case for autoscaling).
 */
@Data
public class EnvironmentStats {

    @Nullable
    private String slug;

    @Nullable
    private ResourcePhase aggregateStatus;

    @Valid
    @Nullable
    private Quantity configuredCpu;

    @Valid
    @Nullable
    private Quantity configuredMemory;
}

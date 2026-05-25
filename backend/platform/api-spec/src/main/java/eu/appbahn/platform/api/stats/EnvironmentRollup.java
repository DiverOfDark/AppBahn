package eu.appbahn.platform.api.stats;

import eu.appbahn.shared.crd.ResourcePhase;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Per-environment aggregate snapshot embedded in {@link ProjectStats#getEnvs()}.
 * The {@code status} is the project-rollup view of the environment: the worst
 * status across the environment's resources (see {@code StatsService} for the
 * exact precedence order).
 */
@Data
public class EnvironmentRollup {

    @Nullable
    private String slug;

    @Nullable
    private ResourcePhase status;
}

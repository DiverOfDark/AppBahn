package eu.appbahn.platform.api.resource;

import java.time.LocalDate;
import lombok.Data;
import org.springframework.lang.Nullable;

/** One day's worth of deployment counts inside a {@link DeploymentStats} window. */
@Data
public class DeploymentStatsBucket {

    /** UTC calendar day this bucket represents (date only, no time). */
    @Nullable
    private LocalDate day;

    /** Total deployments minted on {@link #day}, regardless of outcome. */
    @Nullable
    private Integer count;

    /** Subset of {@link #count} that reached {@code ACTIVE} or {@code BUILT}. */
    @Nullable
    private Integer success;

    /** Subset of {@link #count} that reached {@code FAILED} or {@code CANCELED}. */
    @Nullable
    private Integer failure;
}

package eu.appbahn.platform.api.stats;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Bulk-rollup counters for a single project, returned by {@code GET /projects/stats}.
 * The {@code services} count is the number of resources across all environments in the
 * project; {@code envs} is the environment count plus per-environment status summaries.
 * Server-side rollup — no per-environment fan-out in the SPA.
 */
@Data
public class ProjectStats {

    @Nullable
    private String slug;

    private long services;

    private long deploys7d;

    /**
     * Percentage of resources currently in {@code READY} (0 — 100). When the project has no
     * resources the value is 100.0 — vacuously healthy. Snapshot of the current state, not a
     * historic rolling average.
     */
    private double uptimePct;

    @Valid
    @Nullable
    private OffsetDateTime lastDeployAt;

    @Valid
    private List<EnvironmentRollup> envs = new ArrayList<>();
}

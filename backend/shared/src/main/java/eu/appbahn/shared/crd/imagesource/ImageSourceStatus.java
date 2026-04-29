package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.crd.generator.annotation.PrinterColumn;
import java.time.Instant;
import java.util.List;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageSourceStatus {

    /** Latest commit operator has seen on the upstream branch (git type only). */
    @PrinterColumn(name = "COMMIT", priority = 2)
    private String observedCommit;

    /** Latest successful artifact (image-only mirror in PR1; built artifacts in PR2). */
    private LatestArtifact latestArtifact;

    /**
     * Wall-clock instant of the operator's last poll attempt, success or failure. Excluded
     * from {@code statusEquals} comparison so a no-op poll doesn't churn status patches.
     */
    private Instant lastPollAt;

    /** Set when a webhook arrives. Inert in PR1 — wired up in #179. */
    private Instant lastWebhookAt;

    private Long observedGeneration;
    private List<ImageSourceCondition> conditions;
}

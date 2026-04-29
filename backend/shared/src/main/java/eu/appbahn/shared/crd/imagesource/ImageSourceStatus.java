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

    /** Latest successful artifact (image, mirror, or build output). */
    private LatestArtifact latestArtifact;

    /**
     * The currently-building or just-queued build for this ImageSource. Holds at most one
     * entry in lifecycle {@link BuildLifecycle#QUEUED} or {@link BuildLifecycle#BUILDING}.
     */
    private PendingBuild pendingBuild;

    /**
     * The next build waiting to run after {@link #pendingBuild} finishes — the second slot in
     * the "1 BUILDING + 1 QUEUED" cap. New commits arriving while this slot is occupied
     * supersede the entry (emit {@link BuildLifecycle#SUPERSEDED}) and replace it.
     */
    private PendingBuild queuedBuild;

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

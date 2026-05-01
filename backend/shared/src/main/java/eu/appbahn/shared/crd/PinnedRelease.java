package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/**
 * Snapshot of a historical artifact the operator must run instead of the bound ImageSource's
 * current {@code latestArtifact}. Set by the platform on rollback to give Vercel/Railway/Heroku-style
 * "pin to a previous deployment, no rebuild" semantics — independent of ImageSource type.
 *
 * <p>Presence of this field is the toggle: when non-null the operator renders the pod template
 * from these fields and ignores the ImageSource's latestArtifact. Clearing it (via
 * {@code POST /resources/{slug}/unpin}) immediately rolls the Resource back to whatever the
 * ImageSource's current latestArtifact is.
 *
 * <p>The ImageSource is unaffected — it keeps building, mints deployment audit rows, but those
 * builds don't roll the Resource until the pin is cleared.
 *
 * <p>The image's own ENTRYPOINT/CMD is authoritative for the run command. To override at the
 * Resource level, set {@code spec.commandOverride}; that override applies regardless of whether
 * {@code pinnedRelease} is set.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PinnedRelease {
    /** Source commit the pinned image was built from. Null when the artifact is not git-traceable. */
    private String sourceCommit;

    /** Image ref to run (digest-pinned ideally). Required. */
    private String imageRef;

    /** Wall-clock time the pin was applied. */
    private Instant pinnedAt;

    /** Deployment audit row this pin was sourced from (for UI / audit traceability). */
    private UUID pinnedFromDeploymentId;
}

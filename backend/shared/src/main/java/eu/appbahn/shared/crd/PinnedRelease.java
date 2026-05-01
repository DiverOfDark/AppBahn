package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
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
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PinnedRelease {
    /** Source commit the pinned image was built from. Null when the artifact is not git-traceable. */
    private String sourceCommit;

    /** Image ref to run (digest-pinned ideally). Required. */
    private String imageRef;

    /** Run command override at the time the artifact was built. Null falls back to image default. */
    private List<String> runCommand;

    /** Wall-clock time the pin was applied. */
    private Instant pinnedAt;

    /** Deployment audit row this pin was sourced from (for UI / audit traceability). */
    private UUID pinnedFromDeploymentId;
}

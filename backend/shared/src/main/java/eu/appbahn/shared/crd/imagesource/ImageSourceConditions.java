package eu.appbahn.shared.crd.imagesource;

/**
 * Standard condition types and reasons surfaced on {@link ImageSourceStatus#getConditions()}.
 * No string-literal duplication across the reconciler.
 */
public final class ImageSourceConditions {

    private ImageSourceConditions() {}

    public static final String TYPE_READY = "Ready";
    public static final String TYPE_CONFIG_INVALID = "ConfigInvalid";
    public static final String TYPE_OWNER_NOT_RESOLVED = "OwnerNotResolved";
    public static final String TYPE_UPSTREAM_NOT_READY = "UpstreamNotReady";
    public static final String TYPE_UPSTREAM_MISSING = "UpstreamMissing";

    public static final String STATUS_TRUE = "True";
    public static final String STATUS_FALSE = "False";
    public static final String STATUS_UNKNOWN = "Unknown";

    public static final String REASON_OBSERVED = "Observed";
    public static final String REASON_PINNED = "Pinned";
    public static final String REASON_PROMOTED = "Promoted";
    public static final String REASON_PENDING = "Pending";
    public static final String REASON_POLL_FAILED = "PollFailed";
    public static final String REASON_BAD_SPEC = "BadSpec";
    public static final String REASON_VALID = "Valid";
    public static final String REASON_NO_MATCH = "NoMatch";
    public static final String REASON_AMBIGUOUS = "Ambiguous";
    public static final String REASON_BOUND = "Bound";
    public static final String REASON_NO_UPSTREAM_ARTIFACT = "NoUpstreamArtifact";
    public static final String REASON_UPSTREAM_GONE = "UpstreamGone";
    public static final String REASON_AWAITING_PIN = "AwaitingPin";
}

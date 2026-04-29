package eu.appbahn.shared.crd.imagesource;

/**
 * Standard condition types and reasons surfaced on {@link ImageSourceStatus#getConditions()}.
 * No string-literal duplication across the reconciler.
 */
public final class ImageSourceConditions {

    private ImageSourceConditions() {}

    public static final String TYPE_READY = "Ready";
    public static final String TYPE_CONFIG_INVALID = "ConfigInvalid";

    public static final String STATUS_TRUE = "True";
    public static final String STATUS_FALSE = "False";
    public static final String STATUS_UNKNOWN = "Unknown";

    public static final String REASON_OBSERVED = "Observed";
    public static final String REASON_PINNED = "Pinned";
    public static final String REASON_PENDING = "Pending";
    public static final String REASON_POLL_FAILED = "PollFailed";
    public static final String REASON_BAD_SPEC = "BadSpec";
    public static final String REASON_VALID = "Valid";
}

package eu.appbahn.platform.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Deployment trigger types. */
public enum TriggerType {
    @JsonProperty("Manual")
    MANUAL,

    @JsonProperty("Polling")
    POLLING,

    @JsonProperty("Webhook")
    WEBHOOK,

    @JsonProperty("AutoPromotion")
    AUTO_PROMOTION,

    @JsonProperty("Rollback")
    ROLLBACK,

    /** User explicitly bumped {@code Resource.spec.restartGeneration} — re-roll, no rebuild. */
    @JsonProperty("ManualRestart")
    MANUAL_RESTART,

    /** {@code Resource.spec.config.env} changed and current image is reused — re-roll, no rebuild. */
    @JsonProperty("EnvChange")
    ENV_CHANGE,

    /**
     * User cleared {@code Resource.spec.pinnedRelease}, resuming follow of the bound ImageSource's
     * {@code latestArtifact}. Re-roll, no rebuild.
     */
    @JsonProperty("Unpin")
    UNPIN
}

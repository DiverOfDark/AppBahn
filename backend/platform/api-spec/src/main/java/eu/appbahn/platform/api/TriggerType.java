package eu.appbahn.platform.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Deployment trigger types — serialized values match the public REST contract (lowercase, hyphenated). */
public enum TriggerType {
    @JsonProperty("manual")
    MANUAL,
    @JsonProperty("polling")
    POLLING,
    @JsonProperty("webhook")
    WEBHOOK,
    @JsonProperty("auto-promotion")
    AUTO_PROMOTION,
    @JsonProperty("rollback")
    ROLLBACK,
    /** User explicitly bumped {@code Resource.spec.restartGeneration} — re-roll, no rebuild. */
    @JsonProperty("manual-restart")
    MANUAL_RESTART,
    /** {@code Resource.spec.config.env} changed and current image is reused — re-roll, no rebuild. */
    @JsonProperty("env-change")
    ENV_CHANGE;
}

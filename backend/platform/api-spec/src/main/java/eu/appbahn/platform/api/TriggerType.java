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
    ROLLBACK;
}

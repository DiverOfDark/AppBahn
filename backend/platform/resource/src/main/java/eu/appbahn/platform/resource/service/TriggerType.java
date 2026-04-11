package eu.appbahn.platform.resource.service;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Deployment trigger types — values match the OpenAPI enum (lowercase, hyphenated). */
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

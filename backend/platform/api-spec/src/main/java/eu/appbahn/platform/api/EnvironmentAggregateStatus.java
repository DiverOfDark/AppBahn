package eu.appbahn.platform.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Rollup of an environment's child resource statuses, surfaced on the listing payload so the
 * console can render a status dot per env tab without fetching every resource. Precedence (worst
 * wins): {@link #FAILED} &gt; {@link #DEGRADED} &gt; {@link #PENDING} &gt; {@link #HEALTHY} &gt;
 * {@link #UNKNOWN}.
 */
public enum EnvironmentAggregateStatus {
    @JsonProperty("Failed")
    FAILED,

    @JsonProperty("Degraded")
    DEGRADED,

    @JsonProperty("Pending")
    PENDING,

    @JsonProperty("Healthy")
    HEALTHY,

    @JsonProperty("Unknown")
    UNKNOWN
}

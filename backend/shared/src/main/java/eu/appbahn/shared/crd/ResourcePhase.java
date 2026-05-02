package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ResourcePhase {
    @JsonProperty("Pending")
    PENDING,

    @JsonProperty("Ready")
    READY,

    @JsonProperty("Restarting")
    RESTARTING,

    @JsonProperty("Degraded")
    DEGRADED,

    @JsonProperty("Error")
    ERROR,

    @JsonProperty("Stopped")
    STOPPED
}

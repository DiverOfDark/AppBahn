package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ResourcePhase {
    PENDING,
    READY,
    RESTARTING,
    DEGRADED,
    ERROR,
    STOPPED;

    @JsonValue
    public String getValue() {
        return name();
    }
}

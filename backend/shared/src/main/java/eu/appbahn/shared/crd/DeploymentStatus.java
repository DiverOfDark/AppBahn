package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DeploymentStatus {
    QUEUED,
    AWAITING_APPROVAL,
    BUILDING,
    COPYING_IMAGE,
    DEPLOYING,
    SUCCEEDED,
    FAILED,
    REJECTED;

    @JsonValue
    public String getValue() {
        return name();
    }
}

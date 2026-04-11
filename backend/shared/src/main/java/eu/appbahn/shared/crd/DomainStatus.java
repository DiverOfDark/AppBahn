package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DomainStatus {
    PENDING,
    ACTIVE,
    ERROR;

    @JsonValue
    public String getValue() {
        return name();
    }
}

package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum DomainStatus {
    @JsonProperty("Pending")
    PENDING,

    @JsonProperty("Active")
    ACTIVE,

    @JsonProperty("Error")
    ERROR
}

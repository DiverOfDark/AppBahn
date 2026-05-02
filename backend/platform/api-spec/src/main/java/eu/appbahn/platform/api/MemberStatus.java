package eu.appbahn.platform.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum MemberStatus {
    @JsonProperty("Active")
    ACTIVE,

    @JsonProperty("Pending")
    PENDING
}

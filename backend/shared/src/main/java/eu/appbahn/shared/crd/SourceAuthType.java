package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SourceAuthType {
    @JsonProperty("none")
    NONE,
    @JsonProperty("basic")
    BASIC,
    @JsonProperty("ssh_key")
    SSH_KEY;
}

package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ExposeMode {
    @JsonProperty("none")
    NONE,
    @JsonProperty("ingress")
    INGRESS,
    @JsonProperty("tcp")
    TCP;
}

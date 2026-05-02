package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ExposeMode {
    @JsonProperty("None")
    NONE,
    @JsonProperty("Ingress")
    INGRESS,
    @JsonProperty("Tcp")
    TCP;
}

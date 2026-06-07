package eu.appbahn.platform.api.tunnel;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Which time-series the operator should run against Prometheus for a {@link QueryMetrics}. */
public enum MetricKind {
    @JsonProperty("Cpu")
    CPU,
    @JsonProperty("Ram")
    RAM,
    @JsonProperty("NetworkInbound")
    NETWORK_INBOUND,
    @JsonProperty("NetworkOutbound")
    NETWORK_OUTBOUND
}

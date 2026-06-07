package eu.appbahn.operator.tunnel.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Typed view of one NDJSON line from a Victoria Logs {@code /select/logsql/query} response. Only
 * the fields the operator consumes are mapped; the Kubernetes stream fields ({@code pod},
 * {@code container}) are surfaced via the standard {@code kubernetes.pod_name} /
 * {@code kubernetes.container_name} log labels written by the cluster's logs agent. Unknown fields
 * are ignored so a Victoria Logs minor-version bump doesn't break parsing.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VictoriaLogsLine {

    @JsonProperty("_time")
    private String time;

    @JsonProperty("_msg")
    private String message;

    @JsonProperty("kubernetes.pod_name")
    private String pod;

    @JsonProperty("kubernetes.container_name")
    private String container;
}

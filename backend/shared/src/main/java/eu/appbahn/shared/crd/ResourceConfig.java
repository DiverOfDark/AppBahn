package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.crd.generator.annotation.PreserveUnknownFields;
import io.fabric8.generator.annotation.Max;
import io.fabric8.generator.annotation.Min;
import io.fabric8.kubernetes.api.model.Quantity;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceConfig {

    @PreserveUnknownFields
    private Source source;

    private HostingConfig hosting;
    private NetworkingConfig networking;
    private HealthCheckConfig healthCheck;
    private Map<String, String> env;
    private RunMode runMode;

    /** Returns all port configs, or empty list if none defined. */
    @JsonIgnore
    public List<PortConfig> getPorts() {
        if (networking == null || networking.getPorts() == null) {
            return List.of();
        }
        return networking.getPorts();
    }

    /** Returns the numerically lowest port from networking.ports, or null if none defined. */
    @JsonIgnore
    public Integer getLowestPort() {
        return getPorts().stream()
                .map(PortConfig::getPort)
                .filter(p -> p != null)
                .min(Integer::compareTo)
                .orElse(null);
    }

    /** Returns the first expose mode from networking.ports, or null if none defined. */
    @JsonIgnore
    public ExposeMode getFirstExposeMode() {
        var ports = getPorts();
        return ports.isEmpty() ? null : ports.get(0).getExpose();
    }

    /** Returns true if any port has expose=INGRESS and a valid port number. */
    @JsonIgnore
    public boolean hasIngressPort() {
        return getPorts().stream().anyMatch(p -> p.getPort() != null && p.getExpose() == ExposeMode.INGRESS);
    }

    /** Returns true if any port has expose=TCP and a valid port number. */
    @JsonIgnore
    public boolean hasTcpPort() {
        return getPorts().stream().anyMatch(p -> p.getPort() != null && p.getExpose() == ExposeMode.TCP);
    }

    /** Returns the domain from the first ingress port, or null if none. */
    @JsonIgnore
    public String getIngressDomain() {
        return getIngressPorts().stream()
                .map(PortConfig::getDomain)
                .filter(d -> d != null)
                .findFirst()
                .orElse(null);
    }

    /** Returns only ports with expose=INGRESS and a valid port number. */
    @JsonIgnore
    public List<PortConfig> getIngressPorts() {
        return getPorts().stream()
                .filter(p -> p.getPort() != null && p.getExpose() == ExposeMode.INGRESS)
                .toList();
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HostingConfig {
        private Quantity cpu;
        private Quantity memory;

        @Min(0)
        private Integer minReplicas;

        @Min(0)
        private Integer maxReplicas;

        /**
         * Returns the effective replica count for quota calculation.
         * Uses maxReplicas (worst-case for autoscaling) if set, otherwise replicas.
         */
        @JsonIgnore
        public Integer getEffectiveReplicasForQuota() {
            if (maxReplicas != null) {
                return maxReplicas;
            }
            return minReplicas;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NetworkingConfig {
        private List<PortConfig> ports;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PortConfig {
        @Min(1)
        @Max(65535)
        private Integer port;

        private ExposeMode expose;

        private String domain;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HealthCheckConfig {
        private ProbeConfig readiness;
        private ProbeConfig liveness;
        private ProbeConfig startup;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProbeConfig {
        private HttpGetAction httpGet;
        private TcpSocketAction tcpSocket;
        private ExecAction exec;
        private Integer initialDelaySeconds;
        private Integer periodSeconds;
        private Integer failureThreshold;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HttpGetAction {
        private String path;
        private Integer port;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TcpSocketAction {
        private Integer port;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExecAction {
        private List<String> command;
    }
}

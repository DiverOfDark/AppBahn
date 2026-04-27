package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.crd.generator.annotation.PrinterColumn;
import java.time.Instant;
import java.util.List;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceStatusDetail {

    @PrinterColumn(name = "PHASE", priority = 3)
    private ResourcePhase phase;

    private String message;
    private Long observedGeneration;
    private ReplicaStatus replicas;
    private List<ResourceCondition> conditions;
    private List<CustomDomainStatus> customDomains;
    private List<LinkStatus> links;
    private String primaryDeploymentId;
    private String primaryImage;
    private Instant lastDeploymentTime;
    private Instant lastSyncTime;

    /** The deployment ID the operator is currently processing (from spec.deploymentRevision). */
    private String latestDeploymentId;

    /** The actual status of the latest deployment, derived from K8s rollout status (not resource phase). */
    private DeploymentStatus latestDeploymentStatus;

    /** True when the last attempt to sync this resource to the platform API failed. */
    private Boolean syncFailed;

    /**
     * Human-readable explanation of the most recent container failure — e.g.
     * "container 'app' crash-looped: exit 137 (OOMKilled)". Set whenever a pod
     * shows a non-OK waiting reason or a previous container terminated with a
     * non-zero exit code. Cleared once all containers are healthy.
     */
    private String lastError;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReplicaStatus {
        private int desired;
        private int ready;
        private int updated;
        private int available;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResourceCondition {
        private String type;
        private String status;
        private Instant lastUpdateTime;
        private String reason;
        private String message;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CustomDomainStatus {
        private String domain;
        private int port;
        private DomainStatus status;
        private String dnsCname;
        private String tlsIssuer;
        private Instant tlsExpiresAt;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LinkStatus {
        private String resource;
        private String status;
        private Instant lastSyncTime;
    }
}

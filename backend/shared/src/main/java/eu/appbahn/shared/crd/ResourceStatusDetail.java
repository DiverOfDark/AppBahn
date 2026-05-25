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

    private Instant lastSyncTime;

    /**
     * Operator's view of the artifact currently rolled out — populated from the bound
     * ImageSource's {@code status.latestArtifact} the moment the rollout actually flips to it.
     */
    private ActiveRelease activeRelease;

    /** Platform-side audit row id for the release the operator currently has active. */
    private String observedReleaseId;

    /**
     * Latest {@code spec.restartGeneration} the operator has acknowledged. The reconciler emits a
     * {@code BuildLifecycleEvent(triggeredBy=MANUAL_RESTART)} the first time it observes a bump
     * past this value and updates this field once the new revision is ACTIVE.
     */
    private Long observedRestartGeneration;

    /**
     * Stable hash of {@code spec.config.env} as last acknowledged by the reconciler. When the
     * spec hash diverges and the resolved {@code imageRef} is unchanged, the reconciler emits a
     * {@code BuildLifecycleEvent(triggeredBy=ENV_CHANGE)} (no rebuild — the same artifact rolls
     * with new env vars).
     */
    private String observedEnvHash;

    /**
     * {@code spec.pinnedRelease.imageRef} the reconciler last acknowledged. When it changes
     * — pin set, swapped to a different historical artifact, or cleared (null) — the reconciler
     * mints a fresh release id and emits a {@code BuildLifecycleEvent} with the right
     * {@code triggeredBy} ({@code ROLLBACK} on set/swap, {@code UNPIN} on clear).
     */
    private String observedPinnedImageRef;

    /** Rollout state derived from K8s facts. */
    private RolloutStatus rolloutStatus;

    /** Pods reporting Ready out of {@link ReplicaStatus#getDesired()}. */
    private int replicasReady;

    /** True when the last attempt to sync this resource to the platform API failed. */
    private Boolean syncFailed;

    /**
     * Human-readable explanation of the most recent container failure — e.g.
     * "container 'app' crash-looped: exit 137 (OOMKilled)". Set whenever a pod
     * shows a non-OK waiting reason or a previous container terminated with a
     * non-zero exit code. Cleared once all containers are healthy.
     */
    private String lastError;

    /**
     * Latest outcome of each configured probe (liveness/readiness/startup). Populated by the
     * operator: failures are observed from kubelet {@code Unhealthy} events; successes and
     * measured latency come from a periodic operator-side probe run against the pod IP using
     * the same {@code spec.config.healthCheck} configuration.
     */
    private ProbeStatusBlock probeStatus;

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

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProbeStatusBlock {
        private ProbeOutcome liveness;
        private ProbeOutcome readiness;
        private ProbeOutcome startup;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProbeOutcome {
        /** True if the most recent observation succeeded; false if it failed. */
        private Boolean ok;

        /**
         * Measured latency of the last operator-side probe run. {@code null} for kubelet-event-driven
         * failures (the event payload doesn't carry duration) and for exec probes when exec RBAC is
         * unavailable.
         */
        private Long lastLatencyMs;

        /** Wall-clock time the outcome was recorded. */
        private Instant lastCheckedAt;
    }
}

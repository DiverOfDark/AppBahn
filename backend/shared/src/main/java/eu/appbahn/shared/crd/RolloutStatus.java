package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Rollout state when the Resource resolves its image via {@code spec.release.fromImageSource}.
 * {@code Pending} = no artifact yet (ImageSource hasn't built); {@code Deploying} = K8s rolling;
 * {@code Healthy} = all replicas ready; {@code Degraded} = some replicas serving but rollout
 * incomplete; {@code Failed} = rollout permanently failed (ProgressDeadlineExceeded or terminal
 * pod-state).
 */
public enum RolloutStatus {
    @JsonProperty("Pending")
    Pending,

    @JsonProperty("Deploying")
    Deploying,

    @JsonProperty("Healthy")
    Healthy,

    @JsonProperty("Degraded")
    Degraded,

    @JsonProperty("Failed")
    Failed
}

package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Rollout state when the Resource resolves its image via {@code spec.release.fromImageSource}.
 * {@code Pending} = no artifact yet (ImageSource hasn't built); {@code Deploying} = K8s rolling;
 * {@code Healthy} = all replicas ready; {@code Degraded} = some replicas serving but rollout
 * incomplete; {@code Failed} = rollout permanently failed (ProgressDeadlineExceeded or terminal
 * pod-state).
 */
public enum RolloutStatus {
    Pending,
    Deploying,
    Healthy,
    Degraded,
    Failed;

    @JsonValue
    public String getValue() {
        return name();
    }
}

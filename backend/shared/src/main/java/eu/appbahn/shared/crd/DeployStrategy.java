package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Deployment update strategy applied to the generated K8s Deployment.
 *
 * <p>{@link #ROLLING} maps to the K8s {@code RollingUpdate} strategy (the default — gradually
 * replaces pods while keeping the service available). {@link #RECREATE} maps to K8s {@code Recreate}
 * (terminate all old pods before starting any new ones — necessary for workloads that can't run
 * two versions in parallel, e.g. exclusive file-lock holders).
 */
public enum DeployStrategy {
    @JsonProperty("Recreate")
    RECREATE,

    @JsonProperty("Rolling")
    ROLLING
}

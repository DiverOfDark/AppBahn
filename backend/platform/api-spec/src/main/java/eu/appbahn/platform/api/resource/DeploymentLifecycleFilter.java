package eu.appbahn.platform.api.resource;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-side lifecycle bucket filter for {@code GET /resources/{slug}/deployments}. {@code All}
 * returns every row; the others narrow to a specific outcome bucket.
 *
 * <ul>
 *   <li>{@code Succeeded} → rows in {@link eu.appbahn.shared.crd.imagesource.BuildLifecycle#ACTIVE
 *       ACTIVE} or {@link eu.appbahn.shared.crd.imagesource.BuildLifecycle#BUILT BUILT}.
 *   <li>{@code Failed} → rows in {@link eu.appbahn.shared.crd.imagesource.BuildLifecycle#FAILED
 *       FAILED} or {@link eu.appbahn.shared.crd.imagesource.BuildLifecycle#CANCELED CANCELED}.
 *   <li>{@code Rollback} → rows whose {@code triggered_by} is
 *       {@link eu.appbahn.platform.api.TriggerType#ROLLBACK}.
 * </ul>
 */
public enum DeploymentLifecycleFilter {
    @JsonProperty("All")
    ALL,

    @JsonProperty("Succeeded")
    SUCCEEDED,

    @JsonProperty("Failed")
    FAILED,

    @JsonProperty("Rollback")
    ROLLBACK
}

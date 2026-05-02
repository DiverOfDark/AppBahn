package eu.appbahn.platform.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Platform event a notification webhook may subscribe to. */
public enum WebhookEvent {
    @JsonProperty("DeploymentSucceeded")
    DEPLOYMENT_SUCCEEDED,

    @JsonProperty("DeploymentFailed")
    DEPLOYMENT_FAILED,

    @JsonProperty("DeploymentAwaitingApproval")
    DEPLOYMENT_AWAITING_APPROVAL,

    @JsonProperty("ResourceError")
    RESOURCE_ERROR,

    @JsonProperty("ResourceDegraded")
    RESOURCE_DEGRADED,

    @JsonProperty("ResourceReady")
    RESOURCE_READY,

    @JsonProperty("BuildStarted")
    BUILD_STARTED,

    @JsonProperty("ExposureCreated")
    EXPOSURE_CREATED,

    @JsonProperty("QuotaWarning")
    QUOTA_WARNING
}

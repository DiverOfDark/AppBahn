package eu.appbahn.platform.api;

/** Platform event a notification webhook may subscribe to. */
public enum WebhookEvent {
    DEPLOYMENT_SUCCEEDED,
    DEPLOYMENT_FAILED,
    DEPLOYMENT_AWAITING_APPROVAL,
    RESOURCE_ERROR,
    RESOURCE_DEGRADED,
    RESOURCE_READY,
    BUILD_STARTED,
    EXPOSURE_CREATED,
    QUOTA_WARNING
}

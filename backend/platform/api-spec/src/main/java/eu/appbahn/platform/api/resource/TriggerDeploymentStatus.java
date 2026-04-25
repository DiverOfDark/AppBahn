package eu.appbahn.platform.api.resource;

/** Outcome of a deployment trigger. */
public enum TriggerDeploymentStatus {
    /** New deployment was enqueued behind a build step. */
    QUEUED,
    /** New deployment went straight into the deploying phase (no build needed). */
    DEPLOYING,
    /** Current primary already matches the requested sourceRef — no new deployment created. */
    DUPLICATE
}

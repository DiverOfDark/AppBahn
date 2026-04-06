package eu.appbahn.shared.model;

public enum DeploymentStatus {
    QUEUED,
    AWAITING_APPROVAL,
    BUILDING,
    COPYING_IMAGE,
    DEPLOYING,
    SUCCEEDED,
    FAILED,
    REJECTED
}

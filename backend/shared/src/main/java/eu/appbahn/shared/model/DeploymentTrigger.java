package eu.appbahn.shared.model;

public enum DeploymentTrigger {
    MANUAL,
    POLLING,
    WEBHOOK,
    AUTO_PROMOTION,
    ROLLBACK
}

package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Per-build lifecycle states. Drive the platform's {@code deployment} audit row state machine
 * via {@code BuildLifecycleEvent} and surface as {@code ImageSourceStatus.pendingBuild.lifecycle}.
 *
 * <p>States {@code ACTIVATING}, {@code ACTIVE} are reserved for the Resource-driven rollout
 * half (PR3); the build half emits {@code QUEUED → BUILDING → BUILT/FAILED → SUPERSEDED → CANCELED}.
 */
public enum BuildLifecycle {
    QUEUED,
    BUILDING,
    BUILT,
    FAILED,
    ACTIVATING,
    ACTIVE,
    SUPERSEDED,
    CANCELED;

    @JsonValue
    public String getValue() {
        return name();
    }
}

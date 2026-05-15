package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-build lifecycle states. Drive the platform's {@code deployment} audit row state machine
 * via {@code BuildLifecycleEvent} and surface as {@code ImageSourceStatus.pendingBuild.lifecycle}.
 *
 * <p>States {@code ACTIVATING}, {@code ACTIVE} are reserved for the Resource-driven rollout
 * half (PR3); the build half emits {@code QUEUED → BUILDING → BUILT/FAILED → SUPERSEDED → CANCELED}.
 */
public enum BuildLifecycle {
    @JsonProperty("Queued")
    QUEUED,

    @JsonProperty("Building")
    BUILDING,

    @JsonProperty("Built")
    BUILT,

    @JsonProperty("Failed")
    FAILED,

    @JsonProperty("Activating")
    ACTIVATING,

    @JsonProperty("Active")
    ACTIVE,

    @JsonProperty("Superseded")
    SUPERSEDED,

    @JsonProperty("Canceled")
    CANCELED;

    /**
     * True for lifecycles that are end-of-life for a deployment row. Terminal rows are an audit
     * record of what happened and must never transition to another lifecycle — late events that
     * arrive after a row has been superseded, failed, or canceled are ignored.
     */
    public boolean isTerminal() {
        return this == SUPERSEDED || this == FAILED || this == CANCELED;
    }
}

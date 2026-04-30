package eu.appbahn.platform.api.tunnel;

import eu.appbahn.platform.api.TriggerType;
import eu.appbahn.shared.crd.imagesource.BuildLifecycle;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Per-build lifecycle transition emitted by the operator: drives the platform's
 * {@code deployment} audit row state machine. Idempotent on {@code (deploymentId, lifecycle)}
 * — replaying the same event reapplies the same row update.
 *
 * <p>{@code deploymentId} is allocated by the operator when it first sees a commit and rides
 * with the build through every transition. Builds the platform never heard about (cross-cluster
 * delivery race) get inserted on first event for the (imageSource, deploymentId) tuple.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BuildLifecycleEvent extends OperatorEvent {

    private String imageSourceName;

    private String imageSourceNamespace;

    private String deploymentId;

    private BuildLifecycle lifecycle;

    private String sourceCommit;

    private String imageRef;

    private String errorMessage;

    /**
     * Audit reason — set on the event that mints a deployment row so the platform can record
     * the right trigger. Typical values: {@code POLLING}/{@code WEBHOOK} for build events,
     * {@code MANUAL_RESTART}/{@code ENV_CHANGE} for the release-only flows that skip the
     * build half. Optional: legacy build events leave this null and the handler defaults to
     * {@code POLLING}.
     */
    private TriggerType triggeredBy;

    private Instant occurredAt;

    public BuildLifecycleEvent() {
        setType("build-lifecycle-event");
    }
}

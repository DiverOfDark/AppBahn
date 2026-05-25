package eu.appbahn.platform.api.tunnel;

import lombok.Data;

/**
 * Tells the operator to re-deploy the same source as a prior deployment. The platform has
 * already minted a fresh {@code deployment} audit row in lifecycle {@code QUEUED}; this command
 * carries that row's id so the operator's emitted {@code BuildLifecycleEvent}s land on it
 * instead of minting a duplicate.
 *
 * <ul>
 *   <li>For {@code type=git} ImageSources, the operator writes a fresh {@code pendingBuild}
 *       (with this {@code deploymentId}) bypassing the orchestrator's "already built / already
 *       failed" dedupe on {@code sourceCommit}. The next reconcile arms the Job.
 *   <li>For {@code type=image} ImageSources, there is no build to re-run — the operator bumps
 *       the sibling Resource's {@code spec.restartGeneration} so the rollout half re-fires and
 *       the {@code ReleaseLifecycleEmitter} emits {@code ACTIVATING} for the new deployment id.
 * </ul>
 */
@Data
public class RetryBuild {

    /** SSE {@code event:} name that carries this payload. */
    public static final String EVENT_NAME = "retry-build";

    private String correlationId;
    private String namespace;

    /** Name of the {@code ImageSource} CR whose build is being retried. */
    private String imageSourceName;

    /** Platform-side deployment row id minted for this retry. */
    private String deploymentId;

    /**
     * Source commit to re-build (git type). {@code null} for non-git ImageSources, which have
     * no source-commit identity (the artifact is the source).
     */
    private String sourceCommit;

    /**
     * Pre-built image digest to re-roll (image type). {@code null} for git, which produces a
     * fresh digest on every build.
     */
    private String imageRef;
}

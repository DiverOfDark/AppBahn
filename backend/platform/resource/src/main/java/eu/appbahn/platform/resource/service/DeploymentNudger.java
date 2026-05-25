package eu.appbahn.platform.resource.service;

/**
 * Tunnel-side notifier used by {@link DeploymentService} for cancel/retry semantics. The
 * platform decides the audit-row state machine; the operator owns the in-cluster effect
 * (delete the in-flight Job, re-arm a build, bump {@code restartGeneration}). This interface
 * stays in the resource module so the production wiring can live in {@code :platform:tunnel}
 * without leaking the tunnel internals back into resource code.
 */
public interface DeploymentNudger {

    /**
     * Tell the operator owning {@code namespace} to abort the in-flight build for
     * {@code imageSourceName}. The {@code deploymentId} is the platform-side audit row the
     * operator's emitted {@code BuildLifecycleEvent} should land on.
     */
    void cancelBuild(String namespace, String imageSourceName, String deploymentId);

    /**
     * Tell the operator owning {@code namespace} to re-deploy {@code imageSourceName} as the
     * given {@code deploymentId}. {@code sourceCommit} is non-null for git ImageSources;
     * {@code imageRef} is non-null for image ImageSources. Both may be set for
     * imageSource-type promotions where the platform knows both pieces.
     */
    void retryBuild(
            String namespace, String imageSourceName, String deploymentId, String sourceCommit, String imageRef);
}

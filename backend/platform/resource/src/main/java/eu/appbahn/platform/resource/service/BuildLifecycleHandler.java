package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.TriggerType;
import eu.appbahn.platform.resource.entity.DeploymentEntity;
import eu.appbahn.platform.resource.repository.DeploymentRepository;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.shared.crd.imagesource.BuildLifecycle;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists {@code BuildLifecycleEvent}s into the {@code deployment} audit table. Idempotent on
 * {@code (deploymentId, lifecycle)} — reapplying the same event reapplies the same row update.
 *
 * <p>Handles both the build half ({@code QUEUED → BUILDING → BUILT/FAILED → SUPERSEDED/CANCELED})
 * and the rollout half ({@code BUILT → ACTIVATING → ACTIVE/FAILED}) of the lifecycle. The
 * legacy {@code status} column is left {@code null} on rows minted from this handler.
 */
@Service
public class BuildLifecycleHandler {

    private static final Logger log = LoggerFactory.getLogger(BuildLifecycleHandler.class);

    private final DeploymentRepository deploymentRepository;
    private final EnvironmentRepository environmentRepository;

    public BuildLifecycleHandler(
            DeploymentRepository deploymentRepository, EnvironmentRepository environmentRepository) {
        this.deploymentRepository = deploymentRepository;
        this.environmentRepository = environmentRepository;
    }

    @Transactional
    public void handle(
            String imageSourceName,
            String imageSourceNamespace,
            String deploymentIdRaw,
            BuildLifecycle lifecycle,
            String sourceCommit,
            String imageRef,
            String errorMessage,
            TriggerType triggeredBy) {
        if (deploymentIdRaw == null || deploymentIdRaw.isBlank()) {
            log.warn("BuildLifecycleEvent dropped — deploymentId missing");
            return;
        }
        if (lifecycle == null) {
            log.warn("BuildLifecycleEvent dropped — lifecycle missing");
            return;
        }
        UUID deploymentId;
        try {
            deploymentId = UUID.fromString(deploymentIdRaw);
        } catch (IllegalArgumentException e) {
            log.warn("BuildLifecycleEvent dropped — malformed deploymentId {}", deploymentIdRaw);
            return;
        }
        var existing = deploymentRepository.findById(deploymentId).orElse(null);
        if (existing == null) {
            existing = newDeployment(deploymentId, imageSourceName, imageSourceNamespace, triggeredBy);
            if (existing == null) {
                return;
            }
        }
        existing.setLifecycle(lifecycle);
        if (sourceCommit != null && !sourceCommit.isBlank()) {
            existing.setSourceRef(sourceCommit);
        }
        if (imageRef != null && !imageRef.isBlank()) {
            existing.setImageRef(imageRef);
        }
        if (errorMessage != null) {
            existing.setErrorMessage(errorMessage);
        }
        if (lifecycle == BuildLifecycle.ACTIVE) {
            existing.setPrimary(true);
        }
        deploymentRepository.save(existing);
        log.info(
                "Recorded BuildLifecycleEvent {} → {} (commit={}, image={})",
                deploymentId,
                lifecycle,
                sourceCommit,
                imageRef);
    }

    private DeploymentEntity newDeployment(
            UUID deploymentId, String imageSourceName, String imageSourceNamespace, TriggerType triggeredBy) {
        // Resolve the environment from the namespace ({prefix}-{envSlug} convention). If we
        // can't resolve, the row is still recorded (it lets the operator's events land while
        // the platform catches up) — but we drop here when there's no environment, since the
        // schema requires environment_id.
        EnvironmentEntity env = null;
        if (imageSourceNamespace != null && imageSourceNamespace.startsWith("abp-")) {
            String envSlug = imageSourceNamespace.substring("abp-".length());
            env = environmentRepository.findBySlug(envSlug).orElse(null);
        }
        if (env == null) {
            log.warn(
                    "Cannot create deployment row for {}/{} — environment not found from namespace",
                    imageSourceNamespace,
                    imageSourceName);
            return null;
        }
        var fresh = new DeploymentEntity();
        fresh.setId(deploymentId);
        fresh.setResourceSlug(imageSourceName.length() > 18 ? imageSourceName.substring(0, 18) : imageSourceName);
        fresh.setEnvironmentId(env.getId());
        fresh.setImageSourceName(imageSourceName);
        fresh.setImageSourceNamespace(imageSourceNamespace);
        fresh.setTriggeredBy(triggeredBy != null ? triggeredBy : TriggerType.POLLING);
        return fresh;
    }
}

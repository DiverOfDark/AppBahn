package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.TriggerType;
import eu.appbahn.platform.resource.entity.DeploymentEntity;
import eu.appbahn.platform.resource.repository.DeploymentRepository;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.shared.crd.DeploymentStatus;
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
 * <p>The platform doesn't yet model env-time snapshots / build-config snapshots — those land in
 * PR3 with the Resource-side bundle command. PR2 only persists the build half of the lifecycle.
 */
@Service
public class BuildLifecycleHandler {

    private static final Logger log = LoggerFactory.getLogger(BuildLifecycleHandler.class);

    /** Bridge to legacy {@code status} column — populated alongside {@code lifecycle}. */
    private static final DeploymentStatus LEGACY_STATUS_BRIDGE = DeploymentStatus.QUEUED;

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
            String errorMessage) {
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
            existing = newDeployment(deploymentId, imageSourceName, imageSourceNamespace);
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
        // Bridge to the legacy status column on first persist so the NOT-NULL constraint is
        // satisfied — PR3 retires the column.
        if (existing.getStatus() == null) {
            existing.setStatus(LEGACY_STATUS_BRIDGE);
        }
        deploymentRepository.save(existing);
        log.info(
                "Recorded BuildLifecycleEvent {} → {} (commit={}, image={})",
                deploymentId,
                lifecycle,
                sourceCommit,
                imageRef);
    }

    private DeploymentEntity newDeployment(UUID deploymentId, String imageSourceName, String imageSourceNamespace) {
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
        fresh.setTriggeredBy(TriggerType.POLLING);
        return fresh;
    }
}

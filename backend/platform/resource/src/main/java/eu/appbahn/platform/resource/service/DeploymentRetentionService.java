package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.resource.repository.DeploymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically prunes terminal-state {@code deployment} audit rows beyond the configured per-Resource
 * keep-window. Pin-referenced and in-flight rows are always preserved (see the eligibility filter
 * in {@link DeploymentRepository#pruneByRetentionPolicy}).
 *
 * <p>The schedule, keep-window, and global on/off toggle are config-driven via
 * {@link DeploymentRetentionProperties}. Spring's relaxed binding maps helm env vars (e.g.
 * {@code PLATFORM_DEPLOYMENT_RETENTION_MAXBUILDSPERRESOURCE}) onto the record's components.
 *
 * <p>The cron string on {@code @Scheduled} is resolved as a property placeholder at startup —
 * Spring evaluates {@code ${platform.deployment.retention.schedule-cron:...}} once when registering
 * the scheduled method, so changing the helm value requires a pod restart (consistent with all
 * other helm-driven config in the platform).
 */
@Service
public class DeploymentRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentRetentionService.class);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentRetentionProperties properties;

    public DeploymentRetentionService(
            DeploymentRepository deploymentRepository, DeploymentRetentionProperties properties) {
        this.deploymentRepository = deploymentRepository;
        this.properties = properties;
    }

    @Scheduled(cron = "${platform.deployment.retention.schedule-cron:0 0 3 * * *}")
    @Transactional
    public void prune() {
        if (!properties.enabled()) {
            log.debug("Deployment retention disabled — skipping prune");
            return;
        }
        int max = properties.maxBuildsPerResource();
        if (max <= 0) {
            log.warn(
                    "Deployment retention max-builds-per-resource is {} — refusing to prune (would delete every "
                            + "terminal row); fix platform.deployment.retention.maxBuildsPerResource",
                    max);
            return;
        }
        pruneWith(max);
    }

    /**
     * Run the prune with an explicit {@code max-builds-per-resource} ceiling. Same code path as
     * the scheduled {@link #prune()}, but the caller chooses the ceiling — used by the e2e admin
     * trigger so a single test can prune to {@code max = 2} without affecting other tests'
     * deployment counts via a global config override.
     */
    @Transactional
    public int pruneWith(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("max-builds-per-resource must be > 0, got " + max);
        }
        int deleted = deploymentRepository.pruneByRetentionPolicy(max);
        if (deleted > 0) {
            log.info("Pruned {} deployment audit rows (max-builds-per-resource={})", deleted, max);
        } else {
            log.debug("Deployment retention prune found no eligible rows (max={})", max);
        }
        return deleted;
    }
}

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
import org.springframework.dao.DataIntegrityViolationException;
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
        boolean correlatorMismatch = false;
        if (existing == null) {
            // The operator's deploymentId is the primary correlator, but during reconcile
            // races the operator can emit two distinct ids for the same build before its CR
            // status converges. Fall back to the (namespace, name, sourceCommit) triple so the
            // late event lands on the existing audit row instead of minting a duplicate.
            existing = findInFlightByImageSource(imageSourceName, imageSourceNamespace, sourceCommit);
            if (existing != null) {
                correlatorMismatch = true;
                log.warn(
                        "BuildLifecycleEvent {} → {} carried a different deploymentId than the "
                                + "existing in-flight row {} for {}/{} commit {}; updating the existing row",
                        deploymentId,
                        lifecycle,
                        existing.getId(),
                        imageSourceNamespace,
                        imageSourceName,
                        sourceCommit);
            }
        }
        if (existing == null) {
            existing = newDeployment(deploymentId, imageSourceName, imageSourceNamespace, triggeredBy);
            if (existing == null) {
                return;
            }
        } else if (existing.getLifecycle() != null && existing.getLifecycle().isTerminal()) {
            // A terminal row (SUPERSEDED / FAILED / CANCELED) is the audit record of a
            // closed-out build and must never transition out of that state. Late events
            // arriving after a row was superseded by a newer release, for example, would
            // otherwise resurrect the row and (for ACTIVE) reclaim primary — corrupting
            // the deploy timeline. Drop the event silently.
            log.info(
                    "Ignoring lifecycle transition {} → {} for deployment {} (slug={}) — "
                            + "row is terminal; event arrived after supersede/fail/cancel",
                    existing.getLifecycle(),
                    lifecycle,
                    existing.getId(),
                    existing.getResourceSlug());
            return;
        }
        // When the event's deploymentId doesn't match the existing row's id, the event is
        // from a "ghost build" produced by an operator-side race. Don't let it regress the
        // row's lifecycle — only apply forward transitions.
        if (!correlatorMismatch || advancesLifecycle(existing.getLifecycle(), lifecycle)) {
            existing.setLifecycle(lifecycle);
        }
        if (sourceCommit != null && !sourceCommit.isBlank()) {
            existing.setSourceRef(sourceCommit);
        }
        if (imageRef != null && !imageRef.isBlank()) {
            existing.setImageRef(imageRef);
        }
        if (errorMessage != null) {
            existing.setErrorMessage(errorMessage);
        }
        // For ACTIVE events, atomically flip the primary flag via transferPrimary:
        // setting primary=true here without clearing the previously-primary row would
        // trip the partial unique index idx_deployment_primary (one primary row per
        // resource_slug). transferPrimary issues a single UPDATE that flips the new
        // id to primary and any other row for the same slug to non-primary in one go.
        // Also mark any in-flight predecessors (BUILT / ACTIVATING) as SUPERSEDED so
        // the Deploys tab doesn't show them stuck on "Activating" forever after a
        // newer release takes over. Gate on the row's POST-update lifecycle (not the
        // event parameter): on the ghost-build path advancesLifecycle may have refused
        // the transition, in which case the row never became ACTIVE and must not be
        // promoted to primary.
        UUID newPrimaryId = existing.getLifecycle() == BuildLifecycle.ACTIVE ? existing.getId() : null;
        try {
            deploymentRepository.save(existing);
            if (newPrimaryId != null) {
                deploymentRepository.transferPrimary(existing.getResourceSlug(), newPrimaryId);
                deploymentRepository.supersedeInFlight(existing.getResourceSlug(), existing.getId());
            }
        } catch (DataIntegrityViolationException e) {
            // Concurrent transaction beat us to inserting a row for this (ns, name, commit).
            // Re-read and retry the update in a fresh tx so the operator's HTTP push can ack.
            log.warn(
                    "Concurrent insert raced this BuildLifecycleEvent {} → {} for {}/{} commit {}; "
                            + "re-reading the existing row to merge",
                    deploymentId,
                    lifecycle,
                    imageSourceNamespace,
                    imageSourceName,
                    sourceCommit);
            throw e;
        }
        log.info(
                "Recorded BuildLifecycleEvent {} → {} (commit={}, image={})",
                deploymentId,
                lifecycle,
                sourceCommit,
                imageRef);
    }

    /** Strict forward lifecycle ordering used when merging events from a ghost build. */
    private static boolean advancesLifecycle(BuildLifecycle current, BuildLifecycle incoming) {
        if (current == null) {
            return true;
        }
        return rank(incoming) > rank(current);
    }

    private static int rank(BuildLifecycle l) {
        if (l == null) return -1;
        return switch (l) {
            case QUEUED -> 0;
            case BUILDING -> 1;
            case BUILT -> 2;
            case ACTIVATING -> 3;
            case ACTIVE -> 4;
            case FAILED, SUPERSEDED, CANCELED -> 5;
        };
    }

    private DeploymentEntity findInFlightByImageSource(
            String imageSourceName, String imageSourceNamespace, String sourceCommit) {
        if (imageSourceName == null
                || imageSourceName.isBlank()
                || imageSourceNamespace == null
                || imageSourceNamespace.isBlank()
                || sourceCommit == null
                || sourceCommit.isBlank()) {
            return null;
        }
        var matches = deploymentRepository.findInFlightByImageSourceAndCommit(
                imageSourceNamespace, imageSourceName, sourceCommit);
        return matches.isEmpty() ? null : matches.get(0);
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

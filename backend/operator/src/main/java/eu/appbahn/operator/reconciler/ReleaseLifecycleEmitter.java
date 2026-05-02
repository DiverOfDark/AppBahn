package eu.appbahn.operator.reconciler;

import eu.appbahn.operator.tunnel.OperatorEventPublisher;
import eu.appbahn.operator.tunnel.client.model.BuildLifecycleEvent;
import eu.appbahn.shared.crd.ActiveRelease;
import eu.appbahn.shared.crd.PinnedRelease;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.ResourceStatusDetail;
import eu.appbahn.shared.crd.RolloutStatus;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Detects release-only re-rolls on a Resource (restart, env edit, pin set/clear) and emits
 * {@link BuildLifecycleEvent}s ({@code ACTIVATING → ACTIVE/FAILED}) so the platform appends a
 * deployment audit row with the right {@code triggeredBy}. No build runs in this path — the
 * bound ImageSource is untouched.
 *
 * <p>Detection is stateful via the Resource's status: we track {@code observedRestartGeneration},
 * {@code observedEnvHash}, and {@code observedPinnedImageRef}. A bump in
 * {@code spec.restartGeneration}, an env-hash change with the same {@code imageRef}, or a
 * change in {@code spec.pinnedRelease.imageRef} (set / swap / clear) flips the operator into
 * {@link BuildLifecycle#ACTIVATING} for a fresh {@code observedReleaseId} (a UUID minted here),
 * then advances to {@link BuildLifecycle#ACTIVE} once the rollout reaches
 * {@link RolloutStatus#Healthy} (or {@link BuildLifecycle#FAILED} on terminal failure).
 */
@Component
public class ReleaseLifecycleEmitter {

    private static final Logger log = LoggerFactory.getLogger(ReleaseLifecycleEmitter.class);

    private final OperatorEventPublisher eventPublisher;

    public ReleaseLifecycleEmitter(OperatorEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Inspect the previous status against the new status and the spec, emit lifecycle events for
     * any restart/env-edit/pin transitions, and return the {@code newStatus} updated with
     * {@code observedReleaseId}, {@code observedRestartGeneration}, {@code observedEnvHash},
     * {@code observedPinnedImageRef}.
     */
    public void reconcile(ResourceCrd resource, ResourceStatusDetail previous, ResourceStatusDetail newStatus) {
        if (resource.getSpec() == null) {
            return;
        }
        String boundImageSourceName = boundImageSourceName(resource);
        if (boundImageSourceName == null) {
            return;
        }
        Long specRestartGen = resource.getSpec().getRestartGeneration();
        String specEnvHash = hashEnv(envOf(resource));
        PinnedRelease pin = resource.getSpec().getPinnedRelease();
        String specPinImageRef = pin != null ? pin.getImageRef() : null;
        ActiveRelease active = newStatus.getActiveRelease();
        String imageRef = active != null ? active.getImageRef() : null;

        Long prevRestartGen = previous != null ? previous.getObservedRestartGeneration() : null;
        String prevEnvHash = previous != null ? previous.getObservedEnvHash() : null;
        String prevReleaseId = previous != null ? previous.getObservedReleaseId() : null;
        String prevPinImageRef = previous != null ? previous.getObservedPinnedImageRef() : null;

        boolean restartBumped = !Objects.equals(specRestartGen, prevRestartGen) && specRestartGen != null;
        boolean envChanged = previous != null && !Objects.equals(specEnvHash, prevEnvHash) && imageRef != null;
        boolean pinChanged = previous != null && !Objects.equals(specPinImageRef, prevPinImageRef);

        // First reconcile of a new resource — seed the trackers without emitting (the build-half
        // already minted a row for the initial activation). A Resource that is born with a pin
        // already set (e.g. created from a CRD apply) is treated the same way.
        if (previous == null) {
            newStatus.setObservedRestartGeneration(specRestartGen);
            newStatus.setObservedEnvHash(specEnvHash);
            newStatus.setObservedPinnedImageRef(specPinImageRef);
            return;
        }

        TriggerEnum trigger = null;
        if (pinChanged) {
            // A pin transition wins over a coincidental restart/env edit on the same reconcile —
            // the deployment row should reflect the user-visible cause.
            trigger = specPinImageRef != null ? TriggerEnum.ROLLBACK : TriggerEnum.UNPIN;
        } else if (restartBumped) {
            trigger = TriggerEnum.MANUAL_RESTART;
        } else if (envChanged) {
            trigger = TriggerEnum.ENV_CHANGE;
        }

        if (trigger != null && imageRef != null) {
            // Mint a new release id and emit ACTIVATING. The next reconcile will observe the
            // rollout outcome and emit ACTIVE/FAILED.
            String newReleaseId = UUID.randomUUID().toString();
            newStatus.setObservedReleaseId(newReleaseId);
            emit(
                    boundImageSourceName,
                    resource.getMetadata().getNamespace(),
                    newReleaseId,
                    BuildLifecycleEvent.LifecycleEnum.ACTIVATING,
                    active != null ? active.getSourceCommit() : null,
                    imageRef,
                    null,
                    trigger.toEvent());
            log.info(
                    "Emitted BuildLifecycleEvent(ACTIVATING, triggeredBy={}) for {}/{} releaseId={}",
                    trigger,
                    resource.getMetadata().getNamespace(),
                    resource.getMetadata().getName(),
                    newReleaseId);
            // Stash spec generation now so we don't re-emit on the next reconcile while waiting
            // for ACTIVE. Env hash stays at the pre-change value until rollout finalises so a
            // mid-flight second edit still gets its own row.
            newStatus.setObservedRestartGeneration(specRestartGen);
            if (trigger == TriggerEnum.ENV_CHANGE) {
                newStatus.setObservedEnvHash(specEnvHash);
            } else {
                newStatus.setObservedEnvHash(prevEnvHash);
            }
            // Advance the pin tracker on pin transitions so we don't re-emit; on other triggers
            // hold the prev value (a concurrent pin change on the next reconcile still fires).
            if (trigger == TriggerEnum.ROLLBACK || trigger == TriggerEnum.UNPIN) {
                newStatus.setObservedPinnedImageRef(specPinImageRef);
            } else {
                newStatus.setObservedPinnedImageRef(prevPinImageRef);
            }
            return;
        }

        // No new trigger — observe the previous values and check whether the in-flight release
        // (if any) has reached a terminal state.
        newStatus.setObservedReleaseId(prevReleaseId);
        newStatus.setObservedRestartGeneration(specRestartGen);
        newStatus.setObservedEnvHash(specEnvHash);
        newStatus.setObservedPinnedImageRef(specPinImageRef);

        RolloutStatus rollout = newStatus.getRolloutStatus();
        if (prevReleaseId != null && imageRef != null) {
            // Don't double-emit: the previous status already saw the same rollout state.
            RolloutStatus prevRollout = previous.getRolloutStatus();
            if (rollout == RolloutStatus.Healthy && prevRollout != RolloutStatus.Healthy) {
                emit(
                        boundImageSourceName,
                        resource.getMetadata().getNamespace(),
                        prevReleaseId,
                        BuildLifecycleEvent.LifecycleEnum.ACTIVE,
                        active.getSourceCommit(),
                        imageRef,
                        null,
                        null);
            } else if (rollout == RolloutStatus.Failed && prevRollout != RolloutStatus.Failed) {
                emit(
                        boundImageSourceName,
                        resource.getMetadata().getNamespace(),
                        prevReleaseId,
                        BuildLifecycleEvent.LifecycleEnum.FAILED,
                        active.getSourceCommit(),
                        imageRef,
                        newStatus.getMessage(),
                        null);
            }
        }
    }

    private static String boundImageSourceName(ResourceCrd resource) {
        if (resource.getMetadata() == null) {
            return null;
        }
        String name = resource.getMetadata().getName();
        return (name == null || name.isBlank()) ? null : name;
    }

    private static Map<String, String> envOf(ResourceCrd resource) {
        var config = resource.getSpec().getConfig();
        if (config == null || config.getEnv() == null) {
            return Map.of();
        }
        return config.getEnv();
    }

    static String hashEnv(Map<String, String> env) {
        if (env == null || env.isEmpty()) {
            return "0";
        }
        try {
            // Stable serialization: sorted by key.
            StringBuilder sb = new StringBuilder();
            env.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> sb.append(e.getKey())
                    .append('=')
                    .append(e.getValue() == null ? "" : e.getValue())
                    .append('\n'));
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void emit(
            String imageSourceName,
            String namespace,
            String deploymentId,
            BuildLifecycleEvent.LifecycleEnum lifecycle,
            String sourceCommit,
            String imageRef,
            String errorMessage,
            BuildLifecycleEvent.TriggeredByEnum triggeredBy) {
        var event = new BuildLifecycleEvent();
        event.setImageSourceName(imageSourceName);
        event.setImageSourceNamespace(namespace);
        event.setDeploymentId(deploymentId);
        event.setLifecycle(lifecycle);
        event.setSourceCommit(sourceCommit);
        event.setImageRef(imageRef);
        event.setErrorMessage(errorMessage);
        event.setTriggeredBy(triggeredBy);
        event.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            eventPublisher.emit(event);
        } catch (Exception e) {
            log.warn(
                    "Failed to enqueue release BuildLifecycleEvent for {}/{}: {}",
                    namespace,
                    imageSourceName,
                    e.getMessage());
        }
    }

    enum TriggerEnum {
        MANUAL_RESTART(BuildLifecycleEvent.TriggeredByEnum.MANUAL_RESTART),
        ENV_CHANGE(BuildLifecycleEvent.TriggeredByEnum.ENV_CHANGE),
        ROLLBACK(BuildLifecycleEvent.TriggeredByEnum.ROLLBACK),
        UNPIN(BuildLifecycleEvent.TriggeredByEnum.UNPIN);
        private final BuildLifecycleEvent.TriggeredByEnum eventEnum;

        TriggerEnum(BuildLifecycleEvent.TriggeredByEnum eventEnum) {
            this.eventEnum = eventEnum;
        }

        BuildLifecycleEvent.TriggeredByEnum toEvent() {
            return eventEnum;
        }
    }
}

package eu.appbahn.operator.reconciler.imagesource;

import eu.appbahn.operator.tunnel.OperatorEventPublisher;
import eu.appbahn.shared.crd.imagesource.ImageSourceCondition;
import eu.appbahn.shared.crd.imagesource.ImageSourceConditions;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourceSpec;
import eu.appbahn.shared.crd.imagesource.ImageSourceStatus;
import eu.appbahn.shared.crd.imagesource.LatestArtifact;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reconciles {@link ImageSourceCrd}. For {@code type: git} the reconciler polls the upstream
 * branch on {@code spec.trigger.poll.intervalSeconds} cadence and writes the HEAD SHA into
 * {@code status.observedCommit}. For {@code type: image} it mirrors {@code spec.image.ref}
 * directly into {@code status.latestArtifact} on every reconcile.
 *
 * <p>Adaptive-poll fields ({@code intervalSecondsAfterWebhook}, {@code webhookFreshnessSeconds})
 * are accepted on the spec but stay inert here — webhook delivery lands in a follow-up.
 */
@Component
@ControllerConfiguration
public class ImageSourceReconciler implements Reconciler<ImageSourceCrd>, Cleaner<ImageSourceCrd> {

    private static final Logger log = LoggerFactory.getLogger(ImageSourceReconciler.class);

    static final int DEFAULT_INTERVAL_SECONDS = 60;
    static final int IMAGE_TYPE_RESCHEDULE_SECONDS = 300;

    private final GitClient gitClient;
    private final OperatorEventPublisher eventPublisher;

    public ImageSourceReconciler(GitClient gitClient, OperatorEventPublisher eventPublisher) {
        this.gitClient = gitClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public UpdateControl<ImageSourceCrd> reconcile(ImageSourceCrd cr, Context<ImageSourceCrd> context) {
        String name = cr.getMetadata().getName();
        String namespace = cr.getMetadata().getNamespace();
        log.info("Reconciling ImageSource {}/{}", namespace, name);

        if (cr.getSpec() == null) {
            log.debug("Reconcile called with null spec for {}; skipping", name);
            return UpdateControl.noUpdate();
        }

        ImageSourceSpec spec = cr.getSpec();
        ImageSourceStatus prev = cr.getStatus();
        ImageSourceStatus next = new ImageSourceStatus();
        next.setObservedGeneration(cr.getMetadata().getGeneration());
        if (prev != null) {
            next.setObservedCommit(prev.getObservedCommit());
            next.setLatestArtifact(prev.getLatestArtifact());
            next.setLastWebhookAt(prev.getLastWebhookAt());
        }

        Optional<String> validationError = validate(spec);
        if (validationError.isPresent()) {
            applyConfigInvalid(next, validationError.get());
            return finalize(cr, next);
        }

        return switch (spec.getType()) {
            case GIT -> reconcileGit(cr, spec, next, namespace);
            case IMAGE -> reconcileImage(cr, spec, next);
        };
    }

    @Override
    public DeleteControl cleanup(ImageSourceCrd cr, Context<ImageSourceCrd> context) {
        String name = cr.getMetadata().getName();
        try {
            eventPublisher.emitImageSourceDeleted(name);
            log.info("Notified platform of ImageSource deletion: {}", name);
        } catch (Exception e) {
            log.warn("Failed to notify platform of ImageSource deletion {}: {}", name, e.getMessage());
        }
        return DeleteControl.defaultDelete();
    }

    private UpdateControl<ImageSourceCrd> reconcileGit(
            ImageSourceCrd cr, ImageSourceSpec spec, ImageSourceStatus next, String namespace) {
        Instant now = Instant.now();
        try {
            String head = gitClient.resolveHead(
                    spec.getGit().getRepo(),
                    spec.getGit().getBranch(),
                    namespace,
                    spec.getGit().getCredentialsSecretRef());
            next.setObservedCommit(head);
            next.setLastPollAt(now);
            // Builds are out of scope for this PR — Ready stays false on git type until PR2 lands
            // a build pipeline that fills in latestArtifact.
            applyReady(next, ImageSourceConditions.STATUS_FALSE, ImageSourceConditions.REASON_PENDING, null);
        } catch (Exception e) {
            log.warn(
                    "ImageSource {}/{} poll failed: {}",
                    cr.getMetadata().getNamespace(),
                    cr.getMetadata().getName(),
                    e.getMessage());
            next.setLastPollAt(now);
            applyReady(
                    next,
                    ImageSourceConditions.STATUS_FALSE,
                    ImageSourceConditions.REASON_POLL_FAILED,
                    truncate(e.getMessage()));
        }
        UpdateControl<ImageSourceCrd> control = finalize(cr, next);
        return control.rescheduleAfter(intervalSeconds(spec), TimeUnit.SECONDS);
    }

    private UpdateControl<ImageSourceCrd> reconcileImage(
            ImageSourceCrd cr, ImageSourceSpec spec, ImageSourceStatus next) {
        var artifact = new LatestArtifact();
        artifact.setImageRef(spec.getImage().getRef());
        artifact.setRunCommand(spec.getImage().getRunCommand());
        artifact.setBuiltAt(Instant.now());
        next.setLatestArtifact(artifact);
        applyReady(next, ImageSourceConditions.STATUS_TRUE, ImageSourceConditions.REASON_PINNED, null);
        UpdateControl<ImageSourceCrd> control = finalize(cr, next);
        // Image-type ImageSources don't need fast polling — schedule a slow re-reconcile so
        // observability stays alive without burning the apiserver.
        return control.rescheduleAfter(IMAGE_TYPE_RESCHEDULE_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Returns an error message when the spec violates the "exactly one sub-block matches type"
     * invariant. The CRD schema doesn't enforce this — see Fork 1(a) decision in #109 PR1 — so
     * the operator validates and surfaces a {@code ConfigInvalid} condition.
     */
    static Optional<String> validate(ImageSourceSpec spec) {
        if (spec.getType() == null) {
            return Optional.of("spec.type is required");
        }
        return switch (spec.getType()) {
            case GIT -> {
                if (spec.getGit() == null) {
                    yield Optional.of("spec.type=git but spec.git is missing");
                }
                if (spec.getGit().getRepo() == null || spec.getGit().getRepo().isBlank()) {
                    yield Optional.of("spec.git.repo is required");
                }
                if (spec.getImage() != null) {
                    yield Optional.of("spec.image must not be set when type=git");
                }
                yield Optional.empty();
            }
            case IMAGE -> {
                if (spec.getImage() == null) {
                    yield Optional.of("spec.type=image but spec.image is missing");
                }
                if (spec.getImage().getRef() == null || spec.getImage().getRef().isBlank()) {
                    yield Optional.of("spec.image.ref is required");
                }
                if (spec.getGit() != null) {
                    yield Optional.of("spec.git must not be set when type=image");
                }
                yield Optional.empty();
            }
        };
    }

    private UpdateControl<ImageSourceCrd> finalize(ImageSourceCrd cr, ImageSourceStatus next) {
        if (statusEquals(cr.getStatus(), next)) {
            return UpdateControl.noUpdate();
        }
        cr.setStatus(next);
        try {
            eventPublisher.emitImageSourceSync(cr);
        } catch (Exception e) {
            log.warn(
                    "Failed to enqueue sync for ImageSource {}: {}",
                    cr.getMetadata().getName(),
                    e.getMessage());
        }
        return UpdateControl.patchStatus(cr);
    }

    private static int intervalSeconds(ImageSourceSpec spec) {
        var trigger = spec.getTrigger();
        if (trigger != null
                && trigger.getPoll() != null
                && trigger.getPoll().getIntervalSeconds() != null
                && trigger.getPoll().getIntervalSeconds() > 0) {
            return trigger.getPoll().getIntervalSeconds();
        }
        return DEFAULT_INTERVAL_SECONDS;
    }

    private static void applyReady(ImageSourceStatus status, String boolStatus, String reason, String message) {
        // Reset conditions list so ConfigInvalid (if previously set) is cleared on transition
        // back to a valid spec.
        upsertCondition(status, ImageSourceConditions.TYPE_READY, boolStatus, reason, message);
        removeCondition(status, ImageSourceConditions.TYPE_CONFIG_INVALID);
    }

    private static void applyConfigInvalid(ImageSourceStatus status, String message) {
        upsertCondition(
                status,
                ImageSourceConditions.TYPE_CONFIG_INVALID,
                ImageSourceConditions.STATUS_TRUE,
                ImageSourceConditions.REASON_BAD_SPEC,
                message);
        upsertCondition(
                status,
                ImageSourceConditions.TYPE_READY,
                ImageSourceConditions.STATUS_FALSE,
                ImageSourceConditions.REASON_BAD_SPEC,
                null);
    }

    private static void upsertCondition(
            ImageSourceStatus status, String type, String boolStatus, String reason, String message) {
        List<ImageSourceCondition> conditions =
                status.getConditions() != null ? new ArrayList<>(status.getConditions()) : new ArrayList<>();
        ImageSourceCondition existing = conditions.stream()
                .filter(c -> type.equals(c.getType()))
                .findFirst()
                .orElse(null);
        Instant now = Instant.now();
        if (existing == null) {
            var fresh = new ImageSourceCondition();
            fresh.setType(type);
            fresh.setStatus(boolStatus);
            fresh.setReason(reason);
            fresh.setMessage(message);
            fresh.setLastTransitionTime(now);
            conditions.add(fresh);
        } else {
            // Only stamp lastTransitionTime when the truthy status actually changed; reason or
            // message changes on a stable status keep the original transition time.
            if (!Objects.equals(existing.getStatus(), boolStatus)) {
                existing.setLastTransitionTime(now);
            }
            existing.setStatus(boolStatus);
            existing.setReason(reason);
            existing.setMessage(message);
        }
        status.setConditions(conditions);
    }

    private static void removeCondition(ImageSourceStatus status, String type) {
        if (status.getConditions() == null) {
            return;
        }
        List<ImageSourceCondition> filtered = new ArrayList<>();
        for (var c : status.getConditions()) {
            if (!type.equals(c.getType())) {
                filtered.add(c);
            }
        }
        status.setConditions(filtered.isEmpty() ? null : filtered);
    }

    /**
     * Status-equality check that excludes {@code lastPollAt} — it changes every tick and
     * would force a no-op patch every reconcile. Conditions are compared by (type, status,
     * reason, message); transition time alone doesn't drive a re-emit.
     */
    static boolean statusEquals(ImageSourceStatus a, ImageSourceStatus b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getObservedCommit(), b.getObservedCommit())
                && Objects.equals(a.getObservedGeneration(), b.getObservedGeneration())
                && Objects.equals(a.getLastWebhookAt(), b.getLastWebhookAt())
                && Objects.equals(a.getLatestArtifact(), b.getLatestArtifact())
                && conditionsEqual(a.getConditions(), b.getConditions());
    }

    private static boolean conditionsEqual(List<ImageSourceCondition> a, List<ImageSourceCondition> b) {
        if (a == null && b == null) return true;
        int sa = a == null ? 0 : a.size();
        int sb = b == null ? 0 : b.size();
        if (sa != sb) return false;
        for (int i = 0; i < sa; i++) {
            ImageSourceCondition ca = a.get(i);
            ImageSourceCondition cb = b.get(i);
            if (!Objects.equals(ca.getType(), cb.getType())
                    || !Objects.equals(ca.getStatus(), cb.getStatus())
                    || !Objects.equals(ca.getReason(), cb.getReason())
                    || !Objects.equals(ca.getMessage(), cb.getMessage())) {
                return false;
            }
        }
        return true;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 512 ? message.substring(0, 512) + "..." : message;
    }
}

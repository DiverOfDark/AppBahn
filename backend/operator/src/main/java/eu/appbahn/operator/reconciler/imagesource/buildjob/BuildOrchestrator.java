package eu.appbahn.operator.reconciler.imagesource.buildjob;

import eu.appbahn.operator.tunnel.OperatorEventPublisher;
import eu.appbahn.operator.tunnel.client.model.BuildLifecycleEvent;
import eu.appbahn.shared.crd.imagesource.BuildLifecycle;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourceStatus;
import eu.appbahn.shared.crd.imagesource.LatestArtifact;
import eu.appbahn.shared.crd.imagesource.PendingBuild;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Drives the queue / supersede / lifecycle state machine for one ImageSource. Mutates {@link
 * ImageSourceStatus} (the under-construction status the reconciler will patch) and emits
 * {@link BuildLifecycleEvent}s on transitions. Job CRUD is delegated to {@link
 * BuildJobDependentResource} via the JOSDK workflow — this class never touches the
 * Kubernetes client directly.
 *
 * <p>Slot semantics:
 * <ul>
 *   <li>{@link ImageSourceStatus#getPendingBuild()} — the in-flight build (or just-queued, before
 *       the workflow has materialized its Job).</li>
 *   <li>{@link ImageSourceStatus#getQueuedBuild()} — the next build, waiting for the in-flight
 *       one to terminate. A new commit observed while {@code queuedBuild} is occupied
 *       supersedes whatever is queued (event emitted) and replaces it.</li>
 * </ul>
 */
@Component
public class BuildOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BuildOrchestrator.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BuildJobBuilder jobBuilder;
    private final OperatorEventPublisher eventPublisher;

    public BuildOrchestrator(BuildJobBuilder jobBuilder, OperatorEventPublisher eventPublisher) {
        this.jobBuilder = jobBuilder;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Advance the build state machine. Mutates {@code next} (the under-construction status the
     * reconciler will patch). Idempotent — safe to invoke on every reconcile tick.
     *
     * @param source         the ImageSource CR (current observed state)
     * @param observedCommit the commit observed at the upstream HEAD this poll
     * @param next           the next status the reconciler is preparing to write
     * @param actualJob      the in-flight Job (from the JOSDK secondary cache), or {@code null}
     *                       if none has been observed yet
     */
    public void advance(ImageSourceCrd source, String observedCommit, ImageSourceStatus next, Job actualJob) {
        if (observedCommit == null || observedCommit.isBlank()) {
            return;
        }

        // 1) Reconcile in-flight build against its Job (if any). This may transition pending
        //    into a terminal state (BUILT / FAILED).
        if (next.getPendingBuild() != null && next.getPendingBuild().getJobName() != null) {
            reconcileInFlight(source, next, actualJob);
        }

        // 2) Promote the queued build to in-flight if the previous in-flight one has now
        //    terminated. After this step pending may be a fresh QUEUED entry.
        promoteIfNeeded(source, next);

        // 3) Decide whether we need to enqueue a new build for the observed commit.
        considerNewCommit(source, observedCommit, next);

        // 4) Stamp QUEUED slots with a jobName and flip them to BUILDING. The dependent's
        //    reconcile-precondition watches for jobName!=null, so this is what arms the
        //    workflow to create the Job in the same reconcile pass.
        startQueuedBuild(source, next);
    }

    private void promoteIfNeeded(ImageSourceCrd source, ImageSourceStatus next) {
        PendingBuild pending = next.getPendingBuild();
        if (pending != null && pending.getLifecycle() == BuildLifecycle.BUILT) {
            next.setPendingBuild(null);
        }
        if (pending != null
                && (pending.getLifecycle() == BuildLifecycle.SUPERSEDED
                        || pending.getLifecycle() == BuildLifecycle.CANCELED)) {
            next.setPendingBuild(null);
        }
        // FAILED stays in place — the user (or a new commit) must clear it. Don't auto-retry.
        if (next.getPendingBuild() != null && next.getPendingBuild().getLifecycle() == BuildLifecycle.FAILED) {
            return;
        }
        if (next.getPendingBuild() == null && next.getQueuedBuild() != null) {
            PendingBuild promoted = next.getQueuedBuild();
            next.setQueuedBuild(null);
            next.setPendingBuild(promoted);
            log.info(
                    "ImageSource {}/{}: promoting queued build for commit {} to active slot",
                    source.getMetadata().getNamespace(),
                    source.getMetadata().getName(),
                    promoted.getSourceCommit());
        }
    }

    private void reconcileInFlight(ImageSourceCrd source, ImageSourceStatus next, Job actualJob) {
        PendingBuild pending = next.getPendingBuild();
        if (actualJob == null) {
            // Job not yet materialized (workflow runs after reconcile in explicit-invocation mode,
            // or the informer hasn't observed the just-created Job). Hold steady; the next
            // reconcile will see it.
            return;
        }
        BuildLifecycle inferred = inferLifecycleFromJob(actualJob);
        if (inferred == null || inferred == pending.getLifecycle()) {
            return;
        }
        if (inferred == BuildLifecycle.BUILT) {
            String imageRef = jobBuilder.imageRefFor(source, pending.getSourceCommit());
            LatestArtifact artifact = new LatestArtifact();
            artifact.setSourceCommit(pending.getSourceCommit());
            artifact.setImageRef(imageRef);
            artifact.setBuiltAt(Instant.now());
            next.setLatestArtifact(artifact);
            pending.setLifecycle(BuildLifecycle.BUILT);
            emit(source, pending, BuildLifecycle.BUILT, imageRef, null);
        } else if (inferred == BuildLifecycle.FAILED) {
            String message = extractFailureMessage(actualJob);
            pending.setLifecycle(BuildLifecycle.FAILED);
            pending.setErrorMessage(message);
            emit(source, pending, BuildLifecycle.FAILED, null, message);
        }
    }

    private void considerNewCommit(ImageSourceCrd source, String observedCommit, ImageSourceStatus next) {
        LatestArtifact latest = next.getLatestArtifact();
        boolean alreadyBuilt = latest != null && observedCommit.equals(latest.getSourceCommit());
        PendingBuild pending = next.getPendingBuild();
        boolean alreadyInFlight = pending != null
                && observedCommit.equals(pending.getSourceCommit())
                && !terminalBuildLifecycle(pending.getLifecycle());
        boolean alreadyFailed = pending != null
                && observedCommit.equals(pending.getSourceCommit())
                && pending.getLifecycle() == BuildLifecycle.FAILED;
        boolean alreadyQueued = next.getQueuedBuild() != null
                && observedCommit.equals(next.getQueuedBuild().getSourceCommit());
        if (alreadyBuilt || alreadyInFlight || alreadyQueued || alreadyFailed) {
            return;
        }

        // A different commit arrived while pending is FAILED — clear the failed entry so the
        // new commit can take its slot.
        if (pending != null && pending.getLifecycle() == BuildLifecycle.FAILED) {
            next.setPendingBuild(null);
        }

        // No active build → goes straight into pending slot at QUEUED.
        if (next.getPendingBuild() == null) {
            PendingBuild fresh = freshPending(observedCommit);
            next.setPendingBuild(fresh);
            emit(source, fresh, BuildLifecycle.QUEUED, null, null);
            return;
        }

        // Active build already running. Use queued slot — supersede whatever is there.
        if (next.getQueuedBuild() != null) {
            PendingBuild superseded = next.getQueuedBuild();
            superseded.setLifecycle(BuildLifecycle.SUPERSEDED);
            emit(source, superseded, BuildLifecycle.SUPERSEDED, null, null);
        }
        PendingBuild fresh = freshPending(observedCommit);
        next.setQueuedBuild(fresh);
        emit(source, fresh, BuildLifecycle.QUEUED, null, null);
    }

    private void startQueuedBuild(ImageSourceCrd source, ImageSourceStatus next) {
        PendingBuild pending = next.getPendingBuild();
        if (pending == null || pending.getLifecycle() != BuildLifecycle.QUEUED || pending.getJobName() != null) {
            return;
        }
        String jobName = newJobName(source, pending);
        pending.setJobName(jobName);
        pending.setLifecycle(BuildLifecycle.BUILDING);
        pending.setStartedAt(Instant.now());
        emit(source, pending, BuildLifecycle.BUILDING, null, null);
        log.info(
                "Armed build Job {}/{} for ImageSource commit {} (workflow will create)",
                source.getMetadata().getNamespace(),
                jobName,
                pending.getSourceCommit());
    }

    static boolean terminalBuildLifecycle(BuildLifecycle lifecycle) {
        return lifecycle == BuildLifecycle.BUILT
                || lifecycle == BuildLifecycle.FAILED
                || lifecycle == BuildLifecycle.SUPERSEDED
                || lifecycle == BuildLifecycle.CANCELED;
    }

    /**
     * Map a Kubernetes Job's status to a build lifecycle. {@code null} means "not yet known"
     * (the Job is still pending/running, no terminal condition).
     */
    static BuildLifecycle inferLifecycleFromJob(Job job) {
        if (job == null || job.getStatus() == null) {
            return null;
        }
        if (job.getStatus().getSucceeded() != null && job.getStatus().getSucceeded() > 0) {
            return BuildLifecycle.BUILT;
        }
        if (job.getStatus().getFailed() != null && job.getStatus().getFailed() > 0) {
            return BuildLifecycle.FAILED;
        }
        List<JobCondition> conditions = job.getStatus().getConditions();
        if (conditions != null) {
            for (JobCondition c : conditions) {
                if ("Failed".equals(c.getType()) && "True".equalsIgnoreCase(c.getStatus())) {
                    return BuildLifecycle.FAILED;
                }
                if ("Complete".equals(c.getType()) && "True".equalsIgnoreCase(c.getStatus())) {
                    return BuildLifecycle.BUILT;
                }
            }
        }
        return null;
    }

    private static String extractFailureMessage(Job job) {
        if (job.getStatus() == null) {
            return "build failed";
        }
        return Optional.ofNullable(job.getStatus().getConditions()).orElse(List.of()).stream()
                .filter(c -> "Failed".equals(c.getType()) && c.getMessage() != null)
                .map(JobCondition::getMessage)
                .findFirst()
                .orElse("build failed (no Job condition message)");
    }

    private void emit(
            ImageSourceCrd source, PendingBuild build, BuildLifecycle lifecycle, String imageRef, String errorMessage) {
        var event = new BuildLifecycleEvent();
        event.setImageSourceName(source.getMetadata().getName());
        event.setImageSourceNamespace(source.getMetadata().getNamespace());
        event.setDeploymentId(build.getDeploymentId());
        event.setLifecycle(BuildLifecycleEvent.LifecycleEnum.fromValue(lifecycle.name()));
        event.setSourceCommit(build.getSourceCommit());
        event.setImageRef(imageRef);
        event.setErrorMessage(errorMessage);
        event.setOccurredAt(Instant.now().atOffset(java.time.ZoneOffset.UTC));
        try {
            eventPublisher.emit(event);
        } catch (Exception e) {
            log.warn(
                    "Failed to enqueue BuildLifecycleEvent for {}/{}: {}",
                    source.getMetadata().getNamespace(),
                    source.getMetadata().getName(),
                    e.getMessage());
        }
    }

    private static PendingBuild freshPending(String commit) {
        PendingBuild p = new PendingBuild();
        p.setSourceCommit(commit);
        p.setLifecycle(BuildLifecycle.QUEUED);
        p.setDeploymentId(UUID.randomUUID().toString());
        p.setStartedAt(Instant.now());
        return p;
    }

    /** Job names: {@code build-<imageSource>-<short-commit>-<8-hex>}. K8s name length cap is 63. */
    private static String newJobName(ImageSourceCrd source, PendingBuild pending) {
        String base = "build-" + source.getMetadata().getName() + "-"
                + (pending.getSourceCommit() == null
                        ? "x"
                        : pending.getSourceCommit()
                                .substring(
                                        0, Math.min(8, pending.getSourceCommit().length())));
        if (base.length() > 53) {
            base = base.substring(0, 53);
        }
        byte[] suffix = new byte[4];
        RANDOM.nextBytes(suffix);
        StringBuilder sb = new StringBuilder(base).append('-');
        for (byte b : suffix) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

package eu.appbahn.operator.reconciler.imagesource.buildjob;

import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.crd.imagesource.PendingBuild;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Materializes the K8s {@link Job} for the currently in-flight build slot of an
 * {@link ImageSourceCrd}. Reads {@link ImageSourceCrd#getStatus() pendingBuild.jobName} —
 * assigned by {@link BuildOrchestrator} before the workflow runs — and produces the desired
 * Job via {@link BuildJobBuilder}.
 *
 * <p>{@link Creator}-only: Build Jobs are immutable once created. JOSDK never updates or
 * deletes them; the OwnerReference baked into the Job by {@link BuildJobBuilder} lets
 * Kubernetes cascade-delete on ImageSource deletion, and {@code ttlSecondsAfterFinished} on
 * the Job spec handles post-completion cleanup. Tracking back from a Job to its primary
 * ImageSource uses the JOSDK default annotations (see
 * {@code KubernetesDependentResource.addReferenceHandlingMetadata}).
 */
@KubernetesDependent
public class BuildJobDependentResource extends KubernetesDependentResource<Job, ImageSourceCrd>
        implements Creator<Job, ImageSourceCrd> {

    private final BuildJobBuilder jobBuilder;

    public BuildJobDependentResource(BuildJobBuilder jobBuilder) {
        super(Job.class);
        this.jobBuilder = jobBuilder;
    }

    @Override
    protected Job desired(ImageSourceCrd primary, Context<ImageSourceCrd> context) {
        PendingBuild pending = primary.getStatus() != null ? primary.getStatus().getPendingBuild() : null;
        if (pending == null || pending.getJobName() == null) {
            // BuildJobReconcileCondition gates the workflow on this exact invariant; reaching
            // desired() with no pending slot would be a precondition bug.
            throw new IllegalStateException("BuildJobDependentResource.desired called without a pending build with "
                    + "an assigned jobName for ImageSource "
                    + primary.getMetadata().getName());
        }
        return jobBuilder.build(primary, pending.getSourceCommit(), pending.getDeploymentId(), pending.getJobName());
    }

    /**
     * Tell JOSDK the secondary's identity comes from the pending slot, not from re-running
     * {@link #desired}. This avoids constructing a full Job spec just to look up the existing
     * one in the informer cache.
     */
    @Override
    protected ResourceID targetSecondaryResourceID(ImageSourceCrd primary, Context<ImageSourceCrd> context) {
        PendingBuild pending = primary.getStatus() != null ? primary.getStatus().getPendingBuild() : null;
        String name = pending != null ? pending.getJobName() : null;
        return new ResourceID(name, primary.getMetadata().getNamespace());
    }
}

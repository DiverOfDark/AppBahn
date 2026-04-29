package eu.appbahn.operator.reconciler.imagesource.buildjob;

import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.crd.imagesource.PendingBuild;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

/**
 * Gates {@link BuildJobDependentResource} on the {@link PendingBuild} slot being populated
 * with an assigned {@code jobName}. {@link BuildOrchestrator} fills both fields together
 * before the workflow runs, so this condition holds for exactly the lifetime of an in-flight
 * build.
 *
 * <p>Returning {@code false} when no slot is occupied keeps JOSDK from invoking
 * {@code desired()} (which would NPE) and — since the dependent is {@link
 * io.javaoperatorsdk.operator.processing.dependent.Creator}-only — leaves any previously
 * created Job in place to be reaped via {@code ttlSecondsAfterFinished}.
 */
public class BuildJobReconcileCondition implements Condition<Job, ImageSourceCrd> {

    @Override
    public boolean isMet(
            DependentResource<Job, ImageSourceCrd> dependentResource,
            ImageSourceCrd primary,
            Context<ImageSourceCrd> context) {
        if (primary.getStatus() == null) {
            return false;
        }
        PendingBuild pending = primary.getStatus().getPendingBuild();
        return pending != null && pending.getJobName() != null;
    }
}

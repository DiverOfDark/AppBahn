package eu.appbahn.operator.reconciler;

import eu.appbahn.operator.reconciler.imagesource.ResourceReleaseResolver;
import eu.appbahn.shared.crd.DockerSource;
import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

/**
 * Gates the Deployment dependent on two things: (1) a usable image is resolvable — either via
 * the new {@code spec.release.fromImageSource} chain (sibling ImageSource has a
 * {@code latestArtifact.imageRef}) or via the legacy {@code spec.config.source} DockerSource —
 * and (2) the Resource is not in a stopped state. When this returns {@code false}, JOSDK
 * deletes any existing Deployment instead of reconciling it. Setting {@code spec.stopped=true}
 * drops the Deployment entirely; the Service, Ingress and ConfigMap stay so restart is fast.
 */
public class DeploymentReconcileCondition implements Condition<Deployment, ResourceCrd> {

    @Override
    public boolean isMet(
            DependentResource<Deployment, ResourceCrd> dependentResource,
            ResourceCrd primary,
            Context<ResourceCrd> context) {
        if (primary.getSpec() == null) {
            return false;
        }
        if (Boolean.TRUE.equals(primary.getSpec().getStopped())) {
            return false;
        }
        if (ResourceReleaseResolver.usesReleasePath(primary)) {
            return ResourceReleaseResolver.resolveImageRef(primary, context).isPresent();
        }
        var config = primary.getSpec().getConfig();
        return config != null
                && config.getSource() instanceof DockerSource dockerSource
                && dockerSource.getImage() != null;
    }
}

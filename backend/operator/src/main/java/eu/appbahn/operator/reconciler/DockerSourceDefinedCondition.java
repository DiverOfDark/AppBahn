package eu.appbahn.operator.reconciler;

import eu.appbahn.shared.crd.DockerSource;
import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class DockerSourceDefinedCondition implements Condition<Deployment, ResourceCrd> {

    @Override
    public boolean isMet(
            DependentResource<Deployment, ResourceCrd> dependentResource,
            ResourceCrd primary,
            Context<ResourceCrd> context) {
        if (primary.getSpec() == null) {
            return false;
        }
        var config = primary.getSpec().getConfig();
        return config != null
                && config.getSource() instanceof DockerSource dockerSource
                && dockerSource.getImage() != null;
    }
}

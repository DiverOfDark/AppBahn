package eu.appbahn.operator.reconciler;

import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class EnvDefinedCondition implements Condition<ConfigMap, ResourceCrd> {

    @Override
    public boolean isMet(
            DependentResource<ConfigMap, ResourceCrd> dependentResource,
            ResourceCrd primary,
            Context<ResourceCrd> context) {
        return ResourceCrdUtils.hasEnvVars(primary);
    }
}

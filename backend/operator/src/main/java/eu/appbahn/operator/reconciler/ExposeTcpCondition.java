package eu.appbahn.operator.reconciler;

import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class ExposeTcpCondition implements Condition<Service, ResourceCrd> {

    @Override
    public boolean isMet(
            DependentResource<Service, ResourceCrd> dependentResource,
            ResourceCrd primary,
            Context<ResourceCrd> context) {
        var config = primary.getSpec().getConfig();
        return config != null && config.hasTcpPort();
    }
}

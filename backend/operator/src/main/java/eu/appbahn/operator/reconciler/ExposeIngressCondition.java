package eu.appbahn.operator.reconciler;

import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class ExposeIngressCondition implements Condition<Ingress, ResourceCrd> {

    @Override
    public boolean isMet(
            DependentResource<Ingress, ResourceCrd> dependentResource,
            ResourceCrd primary,
            Context<ResourceCrd> context) {
        if (primary.getSpec() == null) {
            return false;
        }
        var config = primary.getSpec().getConfig();
        if (config == null) {
            return false;
        }
        return config.hasIngressPort() && config.getIngressDomain() != null;
    }
}

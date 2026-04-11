package eu.appbahn.operator.reconciler;

import eu.appbahn.shared.crd.ExposeMode;
import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

/** Condition for the ClusterIP Service — true when any non-TCP port is defined. */
public class PortDefinedCondition implements Condition<HasMetadata, ResourceCrd> {

    @Override
    public boolean isMet(
            DependentResource<HasMetadata, ResourceCrd> dependentResource,
            ResourceCrd primary,
            Context<ResourceCrd> context) {
        var config = primary.getSpec().getConfig();
        return config != null
                && config.getPorts().stream().anyMatch(p -> p.getPort() != null && p.getExpose() != ExposeMode.TCP);
    }
}

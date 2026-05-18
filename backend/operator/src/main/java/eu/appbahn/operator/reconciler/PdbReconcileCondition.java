package eu.appbahn.operator.reconciler;

import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

/**
 * Reconcile precondition for {@link PdbDependentResource}: true when the resource declares a
 * non-null {@code hosting.pdb.minAvailable}. When false, JOSDK's workflow garbage-collects any
 * previously-created PDB.
 */
public class PdbReconcileCondition implements Condition<HasMetadata, ResourceCrd> {

    @Override
    public boolean isMet(
            DependentResource<HasMetadata, ResourceCrd> dependentResource,
            ResourceCrd primary,
            Context<ResourceCrd> context) {
        if (primary.getSpec() == null) {
            return false;
        }
        var config = primary.getSpec().getConfig();
        if (config == null || config.getHosting() == null) {
            return false;
        }
        var pdb = config.getHosting().getPdb();
        return pdb != null && pdb.getMinAvailable() != null;
    }
}

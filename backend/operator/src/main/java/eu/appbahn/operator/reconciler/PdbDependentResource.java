package eu.appbahn.operator.reconciler;

import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

/**
 * Reconciles a {@code policy/v1 PodDisruptionBudget} alongside the Deployment when the resource
 * declares {@code hosting.pdb.minAvailable}. Reconciliation is gated by
 * {@link PdbReconcileCondition}: when {@code pdb} is unset or {@code minAvailable} is null, the
 * dependent is not reconciled and any existing PDB is garbage-collected via the workflow.
 *
 * <p>The PDB's pod selector matches the same labels the operator uses on the Deployment's pod
 * template ({@link Labels#forResource(String)}), so the budget applies to exactly the pods the
 * Deployment manages.
 */
@KubernetesDependent
public class PdbDependentResource extends CRUDKubernetesDependentResource<PodDisruptionBudget, ResourceCrd> {

    public PdbDependentResource() {
        super(PodDisruptionBudget.class);
    }

    @Override
    protected PodDisruptionBudget desired(ResourceCrd primary, Context<ResourceCrd> context) {
        String name = primary.getMetadata().getName();
        String namespace = primary.getMetadata().getNamespace();
        var config = primary.getSpec().getConfig();
        ResourceConfig.HostingConfig hosting = config != null ? config.getHosting() : null;
        Integer minAvailable =
                hosting != null && hosting.getPdb() != null ? hosting.getPdb().getMinAvailable() : null;
        // Guarded by PdbReconcileCondition — minAvailable must be non-null when we get here.
        return new PodDisruptionBudgetBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .withLabels(Labels.forPrimary(primary))
                .endMetadata()
                .withNewSpec()
                .withMinAvailable(new IntOrString(minAvailable))
                .withNewSelector()
                .withMatchLabels(Labels.forResource(name))
                .endSelector()
                .endSpec()
                .build();
    }
}

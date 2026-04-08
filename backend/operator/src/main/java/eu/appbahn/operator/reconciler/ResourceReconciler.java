package eu.appbahn.operator.reconciler;

import eu.appbahn.shared.crd.ResourceCrd;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerConfiguration
public class ResourceReconciler implements Reconciler<ResourceCrd> {

    private static final Logger log = LoggerFactory.getLogger(ResourceReconciler.class);

    @Override
    public UpdateControl<ResourceCrd> reconcile(ResourceCrd resource, Context<ResourceCrd> context) {
        log.info(
                "Reconciling Resource: {} in namespace {}",
                resource.getMetadata().getName(),
                resource.getMetadata().getNamespace());
        return UpdateControl.noUpdate();
    }
}

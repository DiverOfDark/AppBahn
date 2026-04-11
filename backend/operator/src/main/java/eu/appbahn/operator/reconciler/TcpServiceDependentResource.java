package eu.appbahn.operator.reconciler;

import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ExposeMode;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.Map;

/** LoadBalancer Service for ports with expose=TCP. */
@KubernetesDependent
public class TcpServiceDependentResource extends CRUDKubernetesDependentResource<Service, ResourceCrd> {

    public TcpServiceDependentResource() {
        super(Service.class);
    }

    @Override
    protected Service desired(ResourceCrd primary, Context<ResourceCrd> context) {
        String name = primary.getMetadata().getName();
        String namespace = primary.getMetadata().getNamespace();

        var config = primary.getSpec().getConfig();
        var ports = config != null
                ? config.getPorts().stream()
                        .filter(p -> p.getPort() != null && p.getExpose() == ExposeMode.TCP)
                        .toList()
                : java.util.List.<ResourceConfig.PortConfig>of();

        Map<String, String> labels = Labels.forPrimary(primary);

        var builder = new ServiceBuilder()
                .withNewMetadata()
                .withName(name + "-tcp")
                .withNamespace(namespace)
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withType(Labels.SERVICE_TYPE_LOAD_BALANCER)
                .withSelector(Labels.forResource(name));

        for (var p : ports) {
            builder.addNewPort()
                    .withPort(p.getPort())
                    .withNewTargetPort(p.getPort())
                    .withProtocol(Labels.SERVICE_PROTOCOL)
                    .withName("tcp-" + p.getPort())
                    .endPort();
        }

        return builder.endSpec().build();
    }
}

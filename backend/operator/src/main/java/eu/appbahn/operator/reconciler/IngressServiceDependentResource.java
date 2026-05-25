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

/**
 * ClusterIP Service for ports with {@code expose=INGRESS} or {@code expose=NONE}.
 * {@code Ingress} ports are reachable both publicly (via the Ingress) and in-cluster;
 * {@code None} ports are only reachable in-cluster (no Ingress is created).
 * Only {@code expose=TCP} ports are excluded — those land on a LoadBalancer via
 * {@link TcpServiceDependentResource}.
 */
@KubernetesDependent
public class IngressServiceDependentResource extends CRUDKubernetesDependentResource<Service, ResourceCrd> {

    public IngressServiceDependentResource() {
        super(Service.class);
    }

    @Override
    protected Service desired(ResourceCrd primary, Context<ResourceCrd> context) {
        String name = primary.getMetadata().getName();
        String namespace = primary.getMetadata().getNamespace();

        var config = primary.getSpec().getConfig();
        var ports = config != null
                ? config.getPorts().stream()
                        .filter(p -> p.getPort() != null && p.getExpose() != ExposeMode.TCP)
                        .toList()
                : java.util.List.<ResourceConfig.PortConfig>of();

        Map<String, String> labels = Labels.forPrimary(primary);

        var builder = new ServiceBuilder()
                .withNewMetadata()
                .withName(name + "-ingress")
                .withNamespace(namespace)
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withType(Labels.SERVICE_TYPE_CLUSTER_IP)
                .withSelector(Labels.forResource(name));

        for (var p : ports) {
            builder.addNewPort()
                    .withPort(p.getPort())
                    .withNewTargetPort(p.getPort())
                    .withProtocol(Labels.SERVICE_PROTOCOL)
                    .withName("port-" + p.getPort())
                    .endPort();
        }

        return builder.endSpec().build();
    }
}

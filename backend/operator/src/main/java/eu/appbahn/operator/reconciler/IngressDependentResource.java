package eu.appbahn.operator.reconciler;

import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Creates an Ingress for the first port with expose=ingress. Multi-port is planned tech debt. */
@KubernetesDependent
public class IngressDependentResource extends CRUDKubernetesDependentResource<Ingress, ResourceCrd> {

    private final OperatorConfig operatorConfig;

    public IngressDependentResource(OperatorConfig operatorConfig) {
        super(Ingress.class);
        this.operatorConfig = operatorConfig;
    }

    @Override
    protected Ingress desired(ResourceCrd primary, Context<ResourceCrd> context) {
        String ingressClassName = operatorConfig.getIngressClassName();
        String clusterIssuer = operatorConfig.getClusterIssuer();
        String name = primary.getMetadata().getName();
        String namespace = primary.getMetadata().getNamespace();

        var config = primary.getSpec().getConfig();

        var ingressPorts = config != null
                ? config.getIngressPorts()
                : java.util.List.<eu.appbahn.shared.crd.ResourceConfig.PortConfig>of();
        if (ingressPorts.isEmpty()) {
            throw new IllegalStateException("Resource " + name + ": at least one port with expose=ingress is required");
        }

        var firstPort = ingressPorts.get(0);
        int port = firstPort.getPort();
        String domain = firstPort.getDomain();
        if (domain == null) {
            throw new IllegalStateException("Resource " + name + ": domain is required on ingress port");
        }
        Map<String, String> labels = Labels.forPrimary(primary);

        Map<String, String> annotations = new HashMap<>();
        if (clusterIssuer != null) {
            annotations.put(Labels.CERT_MANAGER_CLUSTER_ISSUER_ANNOTATION, clusterIssuer);
        }

        var builder = new IngressBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .withLabels(labels)
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withIngressClassName(ingressClassName);

        if (clusterIssuer != null) {
            String tlsSecretName = name + "-tls";
            builder.withTls(List.of(new io.fabric8.kubernetes.api.model.networking.v1.IngressTLSBuilder()
                    .withHosts(domain)
                    .withSecretName(tlsSecretName)
                    .build()));
        }

        return builder.addNewRule()
                .withHost(domain)
                .withNewHttp()
                .addNewPath()
                .withPath("/")
                .withPathType(Labels.INGRESS_PATH_TYPE)
                .withNewBackend()
                .withNewService()
                .withName(name + "-ingress")
                .withNewPort()
                .withNumber(port)
                .endPort()
                .endService()
                .endBackend()
                .endPath()
                .endHttp()
                .endRule()
                .endSpec()
                .build();
    }
}

package eu.appbahn.operator.reconciler;

import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLSBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.dependent.CRUDKubernetesBulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.KubernetesBulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One Ingress per port with {@code expose=ingress}. The first such port is the primary —
 * its Ingress is named {@code {slug}} and serves {@code {slug}.{baseDomain}}. Subsequent
 * ports yield {@code {slug}-{port}} Ingresses serving {@code {slug}-{port}.{baseDomain}}.
 * The actual host is whatever the platform set on {@code PortConfig.domain}.
 */
@KubernetesDependent
public class IngressDependentResource extends KubernetesDependentResource<Ingress, ResourceCrd>
        implements CRUDKubernetesBulkDependentResource<Ingress, ResourceCrd>,
                KubernetesBulkDependentResource<Ingress, ResourceCrd>,
                GarbageCollected<ResourceCrd> {

    private final OperatorConfig operatorConfig;

    public IngressDependentResource(OperatorConfig operatorConfig) {
        super(Ingress.class);
        this.operatorConfig = operatorConfig;
    }

    @Override
    public Map<ResourceID, Ingress> desiredResources(ResourceCrd primary, Context<ResourceCrd> context) {
        String name = primary.getMetadata().getName();
        String namespace = primary.getMetadata().getNamespace();

        var config = primary.getSpec().getConfig();
        var ingressPorts = config != null ? config.getIngressPorts() : List.<ResourceConfig.PortConfig>of();
        if (ingressPorts.isEmpty()) {
            throw new IllegalStateException("Resource " + name + ": at least one port with expose=ingress is required");
        }

        Map<ResourceID, Ingress> result = new LinkedHashMap<>();
        for (int i = 0; i < ingressPorts.size(); i++) {
            var port = ingressPorts.get(i);
            String ingressName = i == 0 ? name : name + "-" + port.getPort();
            result.put(new ResourceID(ingressName, namespace), buildIngress(primary, port, ingressName));
        }
        return result;
    }

    private Ingress buildIngress(ResourceCrd primary, ResourceConfig.PortConfig port, String ingressName) {
        String namespace = primary.getMetadata().getNamespace();
        String resourceName = primary.getMetadata().getName();
        String ingressClassName = operatorConfig.getIngressClassName();
        String clusterIssuer = operatorConfig.getClusterIssuer();

        Integer portNumber = port.getPort();
        String domain = port.getDomain();
        if (domain == null) {
            throw new IllegalStateException(
                    "Resource " + resourceName + ": domain is required on ingress port " + portNumber);
        }

        Map<String, String> labels = Labels.forPrimary(primary);

        Map<String, String> annotations = new HashMap<>();
        if (clusterIssuer != null) {
            annotations.put(Labels.CERT_MANAGER_CLUSTER_ISSUER_ANNOTATION, clusterIssuer);
        }

        var builder = new IngressBuilder()
                .withNewMetadata()
                .withName(ingressName)
                .withNamespace(namespace)
                .withLabels(labels)
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withIngressClassName(ingressClassName);

        if (clusterIssuer != null) {
            String tlsSecretName = ingressName + "-tls";
            builder.withTls(List.of(new IngressTLSBuilder()
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
                .withName(resourceName + "-ingress")
                .withNewPort()
                .withNumber(portNumber)
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

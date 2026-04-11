package eu.appbahn.operator.reconciler;

import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.HashMap;
import java.util.Map;

@KubernetesDependent
public class ConfigMapDependentResource extends CRUDKubernetesDependentResource<ConfigMap, ResourceCrd> {

    public ConfigMapDependentResource() {
        super(ConfigMap.class);
    }

    @Override
    protected ConfigMap desired(ResourceCrd primary, Context<ResourceCrd> context) {
        String name = primary.getMetadata().getName();
        String namespace = primary.getMetadata().getNamespace();

        var config = primary.getSpec().getConfig();
        Map<String, String> envData = new HashMap<>();
        if (config != null && config.getEnv() != null) {
            envData.putAll(config.getEnv());
        }
        var links = primary.getSpec().getLinks();
        if (links != null) {
            for (var link : links) {
                if (link.getEnv() != null) {
                    envData.putAll(link.getEnv());
                }
            }
        }

        return new ConfigMapBuilder()
                .withNewMetadata()
                .withName(name + "-config")
                .withNamespace(namespace)
                .withLabels(Labels.forPrimary(primary))
                .endMetadata()
                .withData(envData)
                .build();
    }
}

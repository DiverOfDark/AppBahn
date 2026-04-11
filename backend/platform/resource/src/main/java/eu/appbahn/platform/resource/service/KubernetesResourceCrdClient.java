package eu.appbahn.platform.resource.service;

import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.lang.Nullable;

/** Real implementation that delegates to a Fabric8 {@link KubernetesClient}. */
public class KubernetesResourceCrdClient implements ResourceCrdClient {

    private final KubernetesClient kubernetesClient;

    public KubernetesResourceCrdClient(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public void create(ResourceCrd crd) {
        kubernetesClient.resource(crd).create();
    }

    @Override
    public void update(ResourceCrd crd) {
        kubernetesClient.resource(crd).serverSideApply();
    }

    @Override
    public void delete(ResourceCrd crd) {
        kubernetesClient.resource(crd).delete();
    }

    @Override
    @Nullable
    public ResourceCrd get(String slug, String namespace) {
        return kubernetesClient
                .resources(ResourceCrd.class)
                .inNamespace(namespace)
                .withName(slug)
                .get();
    }
}

package eu.appbahn.platform.resource.service;

import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.lang.Nullable;

/** Real implementation that delegates to a Fabric8 {@link KubernetesClient}. */
public class KubernetesResourceCrdClient implements ResourceCrdClient {

    /**
     * Field manager identifying platform-originated writes in Server-Side Apply. Distinct from
     * the operator's own field manager so the two can co-own disjoint field sets on the same CRD
     * without fighting each other.
     */
    private static final String PLATFORM_FIELD_MANAGER = "appbahn-platform";

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
        // SSA with a dedicated field manager + forceConflicts so the operator's concurrent
        // status writes don't surface as 409. managedFields/resourceVersion must be cleared
        // when the object was fetched first — SSA handles conflicts on its own.
        crd.getMetadata().setManagedFields(null);
        crd.getMetadata().setResourceVersion(null);
        kubernetesClient
                .resource(crd)
                .fieldManager(PLATFORM_FIELD_MANAGER)
                .forceConflicts()
                .serverSideApply();
    }

    @Override
    public void delete(ResourceCrd crd) {
        kubernetesClient.resource(crd).delete();
    }

    @Override
    public void delete(String slug, String namespace) {
        kubernetesClient
                .resources(ResourceCrd.class)
                .inNamespace(namespace)
                .withName(slug)
                .delete();
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

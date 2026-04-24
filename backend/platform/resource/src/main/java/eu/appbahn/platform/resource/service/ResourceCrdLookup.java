package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.workspace.service.NamespaceService;
import eu.appbahn.shared.crd.ResourceCrd;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/** Computes the env namespace from its slug and fetches the {@link ResourceCrd} from Kubernetes. */
@Component
public class ResourceCrdLookup {

    private final NamespaceService namespaceService;
    private final ResourceCrdClient crdClient;

    public ResourceCrdLookup(NamespaceService namespaceService, ResourceCrdClient crdClient) {
        this.namespaceService = namespaceService;
        this.crdClient = crdClient;
    }

    @Nullable
    public ResourceCrd get(String slug, String envSlug) {
        return crdClient.get(slug, namespaceService.computeNamespace(envSlug));
    }
}

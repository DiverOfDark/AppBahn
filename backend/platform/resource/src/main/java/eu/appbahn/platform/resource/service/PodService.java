package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.resource.PodsResponse;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.workspace.service.NamespaceService;
import eu.appbahn.shared.model.MemberRole;
import org.springframework.stereotype.Service;

/**
 * Resolves the cluster + namespace for a Resource slug and delegates to the
 * {@link PodInfoSupplier} (tunnel-backed) to fetch live per-pod data. Permission gate:
 * VIEWER on the Resource's environment — same level as listing deployments or reading
 * resource metadata.
 */
@Service
public class PodService {

    private final ResourcePermissionHelper resourcePermissionHelper;
    private final NamespaceService namespaceService;
    private final PodInfoSupplier supplier;

    public PodService(
            ResourcePermissionHelper resourcePermissionHelper,
            NamespaceService namespaceService,
            PodInfoSupplier supplier) {
        this.resourcePermissionHelper = resourcePermissionHelper;
        this.namespaceService = namespaceService;
        this.supplier = supplier;
    }

    public PodsResponse listPods(String slug, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.VIEWER);
        var env = resolved.env();
        String namespace = namespaceService.computeNamespace(env.getSlug());
        return supplier.fetch(env.getTargetCluster(), namespace, slug);
    }
}

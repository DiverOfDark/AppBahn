package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.common.exception.ForbiddenException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.shared.model.MemberRole;
import org.springframework.stereotype.Service;

/**
 * Authorises non-admin reads scoped to a cluster (e.g. capacity headroom for the Scale
 * modal). Platform admins always pass; otherwise the caller must have at least
 * {@link MemberRole#VIEWER} on some environment whose {@code targetCluster} matches.
 */
@Service
public class ClusterPermissionService {

    private final EnvironmentRepository environmentRepository;
    private final PermissionService permissionService;

    public ClusterPermissionService(EnvironmentRepository environmentRepository, PermissionService permissionService) {
        this.environmentRepository = environmentRepository;
        this.permissionService = permissionService;
    }

    public void requireClusterReadAccess(AuthContext ctx, String clusterName) {
        if (ctx.platformAdmin()) {
            return;
        }
        var environments = environmentRepository.findByTargetCluster(clusterName);
        boolean anyAccess = environments.stream()
                .anyMatch(env -> permissionService.resolveEnvironmentRole(ctx, env.getId()) != null);
        if (!anyAccess) {
            throw new ForbiddenException("No environment access on cluster: " + clusterName);
        }
    }
}

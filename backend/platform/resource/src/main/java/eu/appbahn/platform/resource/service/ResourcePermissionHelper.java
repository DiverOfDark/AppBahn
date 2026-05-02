package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.resource.entity.ResourceCacheEntity;
import eu.appbahn.platform.resource.repository.ResourceCacheRepository;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.service.EnvironmentLookupService;
import eu.appbahn.platform.workspace.service.PermissionService;
import eu.appbahn.shared.model.MemberRole;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Shared helper to look up a resource, its environment, and verify permissions.
 * Used by both {@link ResourceService} and {@link DeploymentService}.
 */
@Component
public class ResourcePermissionHelper {

    private final ResourceCacheRepository resourceCacheRepository;
    private final EnvironmentLookupService environmentLookupService;
    private final PermissionService permissionService;

    public ResourcePermissionHelper(
            ResourceCacheRepository resourceCacheRepository,
            EnvironmentLookupService environmentLookupService,
            PermissionService permissionService) {
        this.resourceCacheRepository = resourceCacheRepository;
        this.environmentLookupService = environmentLookupService;
        this.permissionService = permissionService;
    }

    public ResolvedResource resolve(String slug, AuthContext ctx, MemberRole requiredRole) {
        var entity = resourceCacheRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Resource not found: " + slug));

        var env = environmentLookupService.findById(entity.getEnvironmentId());

        permissionService.requireEnvironmentRole(ctx, env.getId(), requiredRole);

        UUID workspaceId = environmentLookupService.getWorkspaceId(env);

        return new ResolvedResource(entity, env, workspaceId);
    }

    public record ResolvedResource(ResourceCacheEntity entity, EnvironmentEntity env, UUID workspaceId) {}
}

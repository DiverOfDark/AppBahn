package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.resource.entity.ResourceCacheEntity;
import eu.appbahn.platform.resource.repository.ResourceCacheRepository;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.platform.workspace.repository.ProjectRepository;
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
    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final PermissionService permissionService;

    public ResourcePermissionHelper(
            ResourceCacheRepository resourceCacheRepository,
            EnvironmentRepository environmentRepository,
            ProjectRepository projectRepository,
            PermissionService permissionService) {
        this.resourceCacheRepository = resourceCacheRepository;
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
        this.permissionService = permissionService;
    }

    public ResolvedResource resolve(String slug, AuthContext ctx, MemberRole requiredRole) {
        var entity = resourceCacheRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Resource not found: " + slug));

        var env = environmentRepository
                .findById(entity.getEnvironmentId())
                .orElseThrow(() -> new NotFoundException("Environment not found for resource: " + slug));

        permissionService.requireEnvironmentRole(ctx, env.getId(), requiredRole);

        var project = projectRepository
                .findById(env.getProjectId())
                .orElseThrow(() -> new NotFoundException("Project not found for environment: " + env.getSlug()));

        return new ResolvedResource(entity, env, project.getWorkspaceId());
    }

    public record ResolvedResource(ResourceCacheEntity entity, EnvironmentEntity env, UUID workspaceId) {}
}

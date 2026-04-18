package eu.appbahn.platform.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.model.ApprovalGatesConfig;
import eu.appbahn.platform.api.model.CreateEnvironmentRequest;
import eu.appbahn.platform.api.model.Environment;
import eu.appbahn.platform.api.model.PagedEnvironmentResponse;
import eu.appbahn.platform.api.model.Quota;
import eu.appbahn.platform.api.model.RegistryConfig;
import eu.appbahn.platform.api.model.SetTargetClusterRequest;
import eu.appbahn.platform.api.model.UpdateEnvironmentRequest;
import eu.appbahn.platform.api.model.UpdateMemberRequest;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.exception.ValidationException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.common.util.JsonUtil;
import eu.appbahn.platform.common.util.PaginationUtil;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.entity.EnvironmentMemberOverrideEntity;
import eu.appbahn.platform.workspace.entity.ProjectEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentMemberOverrideRepository;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.platform.workspace.repository.ProjectRepository;
import eu.appbahn.shared.model.MemberRole;
import eu.appbahn.shared.util.SlugGenerator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentMemberOverrideRepository environmentOverrideRepository;
    private final PermissionService permissionService;
    private final NamespaceService namespaceService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public EnvironmentService(
            EnvironmentRepository environmentRepository,
            ProjectRepository projectRepository,
            EnvironmentMemberOverrideRepository environmentOverrideRepository,
            PermissionService permissionService,
            NamespaceService namespaceService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
        this.environmentOverrideRepository = environmentOverrideRepository;
        this.permissionService = permissionService;
        this.namespaceService = namespaceService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Environment create(CreateEnvironmentRequest req, AuthContext ctx) {
        ProjectEntity project = projectRepository
                .findBySlug(req.getProjectSlug())
                .orElseThrow(() -> new NotFoundException("Project not found: " + req.getProjectSlug()));
        permissionService.requireProjectRole(ctx, project.getId(), MemberRole.ADMIN);

        var entity = new EnvironmentEntity();
        entity.setProjectId(project.getId());
        entity.setName(req.getName());
        entity.setSlug(SlugGenerator.generate(req.getName()));
        if (req.getDescription() != null) {
            entity.setDescription(req.getDescription());
        }
        environmentRepository.save(entity);

        namespaceService.createNamespace(entity.getSlug());

        auditLogService.log(
                ctx,
                "environment.created",
                "environment",
                entity.getSlug(),
                project.getWorkspaceId(),
                Map.of("name", Map.of("old", (Object) "", "new", entity.getName())));

        return EntityMapper.toApi(entity, project.getSlug());
    }

    public PagedEnvironmentResponse list(String projectSlug, Integer page, Integer size, String sort, AuthContext ctx) {
        ProjectEntity project = projectRepository
                .findBySlug(projectSlug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectSlug));
        permissionService.requireProjectRole(ctx, project.getId(), MemberRole.VIEWER);

        var pageable = PaginationUtil.toPageable(page, size, sort);
        Page<EnvironmentEntity> result = environmentRepository.findByProjectId(project.getId(), pageable);

        return toPagedResponse(result, project.getSlug());
    }

    public Environment getBySlug(String slug, AuthContext ctx) {
        var entity = environmentRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + slug));
        permissionService.requireEnvironmentRole(ctx, entity.getId(), MemberRole.VIEWER);
        String projectSlug = projectRepository
                .findById(entity.getProjectId())
                .map(ProjectEntity::getSlug)
                .orElse(null);
        return EntityMapper.toApi(entity, projectSlug);
    }

    @Transactional
    public Environment update(String slug, UpdateEnvironmentRequest req, AuthContext ctx) {
        var entity = environmentRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + slug));
        permissionService.requireEnvironmentRole(ctx, entity.getId(), MemberRole.ADMIN);

        String oldName = entity.getName();
        if (req.getName() != null) {
            entity.setName(req.getName());
        }
        if (req.getDescription() != null) {
            entity.setDescription(req.getDescription());
        }
        environmentRepository.save(entity);

        var project = projectRepository.findById(entity.getProjectId()).orElse(null);
        auditLogService.log(
                ctx,
                "environment.updated",
                "environment",
                entity.getSlug(),
                project != null ? project.getWorkspaceId() : null,
                Map.of("name", Map.of("old", oldName, "new", entity.getName())));

        String projectSlug = project != null ? project.getSlug() : null;
        return EntityMapper.toApi(entity, projectSlug);
    }

    @Transactional
    public void delete(String slug, AuthContext ctx) {
        var entity = environmentRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + slug));
        permissionService.requireEnvironmentRole(ctx, entity.getId(), MemberRole.ADMIN);

        // Cascades to every resource in the namespace.
        namespaceService.deleteNamespace(entity.getSlug());

        var project = projectRepository.findById(entity.getProjectId()).orElse(null);
        auditLogService.log(
                ctx,
                "environment.deleted",
                "environment",
                entity.getSlug(),
                project != null ? project.getWorkspaceId() : null,
                null);

        environmentRepository.delete(entity);
    }

    // --- Role overrides ---

    @Transactional
    public void setMemberRoleOverride(String slug, UUID userId, UpdateMemberRequest req, AuthContext ctx) {
        var entity = environmentRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + slug));
        permissionService.requireEnvironmentRole(ctx, entity.getId(), MemberRole.ADMIN);

        // Override can only elevate above inherited project role
        MemberRole overrideRole = MemberRole.valueOf(req.getRole().getValue());
        MemberRole inherited = permissionService.resolveProjectRole(
                new AuthContext(userId, null, List.of(), false), entity.getProjectId());
        if (inherited != null && overrideRole.ordinal() >= inherited.ordinal()) {
            throw new ValidationException(
                    "Override role must be higher than inherited project role (" + inherited + ")");
        }

        var override = environmentOverrideRepository
                .findByEnvironmentIdAndUserId(entity.getId(), userId)
                .orElseGet(() -> {
                    var o = new EnvironmentMemberOverrideEntity();
                    o.setEnvironmentId(entity.getId());
                    o.setUserId(userId);
                    return o;
                });
        override.setRole(req.getRole().getValue());
        environmentOverrideRepository.save(override);

        UUID workspaceId = projectRepository
                .findById(entity.getProjectId())
                .map(ProjectEntity::getWorkspaceId)
                .orElse(null);
        auditLogService.log(
                ctx,
                "environment.role_override.set",
                "environment",
                entity.getSlug(),
                workspaceId,
                Map.of("userId", userId.toString(), "role", req.getRole().getValue()));
    }

    @Transactional
    public void deleteMemberRoleOverride(String slug, UUID userId, AuthContext ctx) {
        var entity = environmentRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + slug));
        permissionService.requireEnvironmentRole(ctx, entity.getId(), MemberRole.ADMIN);

        var override = environmentOverrideRepository
                .findByEnvironmentIdAndUserId(entity.getId(), userId)
                .orElseThrow(() -> new NotFoundException("Role override not found"));
        environmentOverrideRepository.delete(override);

        UUID workspaceId = projectRepository
                .findById(entity.getProjectId())
                .map(ProjectEntity::getWorkspaceId)
                .orElse(null);
        auditLogService.log(
                ctx, "environment.role_override.removed", "environment", entity.getSlug(), workspaceId, null);
    }

    // --- Settings ---

    public Quota getQuota(String slug, AuthContext ctx) {
        var entity = environmentRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + slug));
        permissionService.requireEnvironmentRole(ctx, entity.getId(), MemberRole.VIEWER);
        return JsonUtil.parseJson(objectMapper, entity.getQuota(), Quota.class);
    }

    @Transactional
    public Quota setQuota(String slug, Quota quota, AuthContext ctx) {
        var entity = environmentRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + slug));
        permissionService.requireEnvironmentRole(ctx, entity.getId(), MemberRole.ADMIN);
        entity.setQuota(JsonUtil.toJson(objectMapper, quota));
        environmentRepository.save(entity);

        UUID workspaceId = projectRepository
                .findById(entity.getProjectId())
                .map(ProjectEntity::getWorkspaceId)
                .orElse(null);
        auditLogService.log(ctx, "environment.quota.updated", "environment", entity.getSlug(), workspaceId, null);
        return quota;
    }

    @Transactional
    public Environment setRegistry(String slug, RegistryConfig registryConfig, AuthContext ctx) {
        var entity = environmentRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + slug));
        permissionService.requireEnvironmentRole(ctx, entity.getId(), MemberRole.ADMIN);
        entity.setRegistry(JsonUtil.toJson(objectMapper, registryConfig));
        environmentRepository.save(entity);

        UUID workspaceId = projectRepository
                .findById(entity.getProjectId())
                .map(ProjectEntity::getWorkspaceId)
                .orElse(null);
        auditLogService.log(ctx, "environment.registry.updated", "environment", entity.getSlug(), workspaceId, null);
        String projectSlug = projectRepository
                .findById(entity.getProjectId())
                .map(ProjectEntity::getSlug)
                .orElse(null);
        return EntityMapper.toApi(entity, projectSlug);
    }

    @Transactional
    public Environment setApprovalGates(String slug, ApprovalGatesConfig approvalGatesConfig, AuthContext ctx) {
        var entity = environmentRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + slug));
        permissionService.requireEnvironmentRole(ctx, entity.getId(), MemberRole.ADMIN);
        entity.setApprovalGates(JsonUtil.toJson(objectMapper, approvalGatesConfig));
        environmentRepository.save(entity);

        UUID workspaceId = projectRepository
                .findById(entity.getProjectId())
                .map(ProjectEntity::getWorkspaceId)
                .orElse(null);
        auditLogService.log(
                ctx, "environment.approval_gates.updated", "environment", entity.getSlug(), workspaceId, null);
        String projectSlug = projectRepository
                .findById(entity.getProjectId())
                .map(ProjectEntity::getSlug)
                .orElse(null);
        return EntityMapper.toApi(entity, projectSlug);
    }

    @Transactional
    public Environment setTargetCluster(String slug, SetTargetClusterRequest req, AuthContext ctx) {
        var entity = environmentRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + slug));
        permissionService.requireEnvironmentRole(ctx, entity.getId(), MemberRole.ADMIN);
        entity.setTargetCluster(req.getClusterName());
        environmentRepository.save(entity);

        UUID workspaceId = projectRepository
                .findById(entity.getProjectId())
                .map(ProjectEntity::getWorkspaceId)
                .orElse(null);
        auditLogService.log(
                ctx, "environment.target_cluster.updated", "environment", entity.getSlug(), workspaceId, null);
        String projectSlug = projectRepository
                .findById(entity.getProjectId())
                .map(ProjectEntity::getSlug)
                .orElse(null);
        return EntityMapper.toApi(entity, projectSlug);
    }

    private PagedEnvironmentResponse toPagedResponse(Page<EnvironmentEntity> page, String projectSlug) {
        var response = new PagedEnvironmentResponse();
        response.setContent(page.getContent().stream()
                .map(e -> EntityMapper.toApi(e, projectSlug))
                .collect(Collectors.toList()));
        response.setPage(page.getNumber());
        response.setSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        return response;
    }
}

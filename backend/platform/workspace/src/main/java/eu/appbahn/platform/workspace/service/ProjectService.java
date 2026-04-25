package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.api.AuditAction;
import eu.appbahn.platform.api.AuditTargetType;
import eu.appbahn.platform.api.Project;
import eu.appbahn.platform.api.Quota;
import eu.appbahn.platform.api.RegistryConfig;
import eu.appbahn.platform.api.UpdateMemberRequest;
import eu.appbahn.platform.api.project.CreateProjectRequest;
import eu.appbahn.platform.api.project.PagedProjectResponse;
import eu.appbahn.platform.api.project.UpdateProjectRequest;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.ConflictException;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.exception.ValidationException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.common.util.PaginationUtil;
import eu.appbahn.platform.workspace.entity.ProjectEntity;
import eu.appbahn.platform.workspace.entity.ProjectMemberOverrideEntity;
import eu.appbahn.platform.workspace.entity.WorkspaceEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.platform.workspace.repository.ProjectMemberOverrideRepository;
import eu.appbahn.platform.workspace.repository.ProjectRepository;
import eu.appbahn.platform.workspace.repository.WorkspaceRepository;
import eu.appbahn.shared.model.MemberRole;
import eu.appbahn.shared.util.SlugGenerator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectMemberOverrideRepository projectOverrideRepository;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    public ProjectService(
            ProjectRepository projectRepository,
            WorkspaceRepository workspaceRepository,
            EnvironmentRepository environmentRepository,
            ProjectMemberOverrideRepository projectOverrideRepository,
            PermissionService permissionService,
            AuditLogService auditLogService) {
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.environmentRepository = environmentRepository;
        this.projectOverrideRepository = projectOverrideRepository;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Project create(CreateProjectRequest req, AuthContext ctx) {
        WorkspaceEntity workspace = workspaceRepository
                .findBySlug(req.getWorkspaceSlug())
                .orElseThrow(() -> new NotFoundException("Workspace not found: " + req.getWorkspaceSlug()));
        permissionService.requireWorkspaceRole(ctx, workspace.getId(), MemberRole.ADMIN);

        var entity = new ProjectEntity();
        entity.setWorkspaceId(workspace.getId());
        entity.setName(req.getName());
        entity.setSlug(SlugGenerator.generate(req.getName()));
        projectRepository.save(entity);

        auditLogService
                .audit(ctx, AuditAction.PROJECT_CREATED)
                .target(AuditTargetType.PROJECT, entity.getSlug())
                .inWorkspace(workspace.getId())
                .inProject(entity.getId())
                .change("name", "", entity.getName())
                .save();

        return EntityMapper.toApi(entity, workspace.getSlug());
    }

    public PagedProjectResponse list(String workspaceSlug, Integer page, Integer size, String sort, AuthContext ctx) {
        WorkspaceEntity workspace = workspaceRepository
                .findBySlug(workspaceSlug)
                .orElseThrow(() -> new NotFoundException("Workspace not found: " + workspaceSlug));
        permissionService.requireWorkspaceRole(ctx, workspace.getId(), MemberRole.VIEWER);

        var pageable = PaginationUtil.toPageable(page, size, sort);
        Page<ProjectEntity> result = projectRepository.findByWorkspaceId(workspace.getId(), pageable);

        return toPagedResponse(result, workspace.getSlug());
    }

    public Project getBySlug(String slug, AuthContext ctx) {
        var entity = projectRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + slug));
        permissionService.requireProjectRole(ctx, entity.getId(), MemberRole.VIEWER);
        String workspaceSlug = workspaceRepository
                .findById(entity.getWorkspaceId())
                .map(WorkspaceEntity::getSlug)
                .orElse(null);
        return EntityMapper.toApi(entity, workspaceSlug);
    }

    @Transactional
    public Project update(String slug, UpdateProjectRequest req, AuthContext ctx) {
        var entity = projectRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + slug));
        permissionService.requireProjectRole(ctx, entity.getId(), MemberRole.ADMIN);

        String oldName = entity.getName();
        if (req.getName() != null) {
            entity.setName(req.getName());
        }
        projectRepository.save(entity);

        auditLogService
                .audit(ctx, AuditAction.PROJECT_UPDATED)
                .target(AuditTargetType.PROJECT, entity.getSlug())
                .inWorkspace(entity.getWorkspaceId())
                .inProject(entity.getId())
                .change("name", oldName, entity.getName())
                .save();

        String workspaceSlug = workspaceRepository
                .findById(entity.getWorkspaceId())
                .map(WorkspaceEntity::getSlug)
                .orElse(null);
        return EntityMapper.toApi(entity, workspaceSlug);
    }

    @Transactional
    public void delete(String slug, AuthContext ctx) {
        var entity = projectRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + slug));
        permissionService.requireProjectRole(ctx, entity.getId(), MemberRole.ADMIN);

        var environments = environmentRepository.findByProjectId(entity.getId());
        if (!environments.isEmpty()) {
            List<String> envSlugs = environments.stream().map(e -> e.getSlug()).collect(Collectors.toList());
            throw new ConflictException("Cannot delete project with existing environments", envSlugs);
        }

        projectRepository.delete(entity);

        auditLogService
                .audit(ctx, AuditAction.PROJECT_DELETED)
                .target(AuditTargetType.PROJECT, entity.getSlug())
                .inWorkspace(entity.getWorkspaceId())
                .inProject(entity.getId())
                .save();
    }

    // --- Role overrides ---

    @Transactional
    public void setMemberRoleOverride(String slug, UUID userId, UpdateMemberRequest req, AuthContext ctx) {
        var entity = projectRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + slug));
        permissionService.requireProjectRole(ctx, entity.getId(), MemberRole.ADMIN);

        // Override can only elevate above inherited workspace role
        MemberRole overrideRole = req.getRole();
        MemberRole inherited = permissionService.resolveWorkspaceRole(
                new AuthContext(userId, null, List.of(), false), entity.getWorkspaceId());
        if (inherited != null && overrideRole.ordinal() >= inherited.ordinal()) {
            throw new ValidationException(
                    "Override role must be higher than inherited workspace role (" + inherited + ")");
        }

        var override = projectOverrideRepository
                .findByProjectIdAndUserId(entity.getId(), userId)
                .orElseGet(() -> {
                    var o = new ProjectMemberOverrideEntity();
                    o.setProjectId(entity.getId());
                    o.setUserId(userId);
                    return o;
                });
        override.setRole(req.getRole().name());
        projectOverrideRepository.save(override);

        auditLogService
                .audit(ctx, AuditAction.PROJECT_ROLE_OVERRIDE_SET)
                .target(AuditTargetType.PROJECT, entity.getSlug())
                .inWorkspace(entity.getWorkspaceId())
                .inProject(entity.getId())
                .detail("userId", userId.toString())
                .detail("role", req.getRole().name())
                .save();
    }

    @Transactional
    public void deleteMemberRoleOverride(String slug, UUID userId, AuthContext ctx) {
        var entity = projectRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + slug));
        permissionService.requireProjectRole(ctx, entity.getId(), MemberRole.ADMIN);

        var override = projectOverrideRepository
                .findByProjectIdAndUserId(entity.getId(), userId)
                .orElseThrow(() -> new NotFoundException("Role override not found"));
        projectOverrideRepository.delete(override);

        auditLogService
                .audit(ctx, AuditAction.PROJECT_ROLE_OVERRIDE_REMOVED)
                .target(AuditTargetType.PROJECT, entity.getSlug())
                .inWorkspace(entity.getWorkspaceId())
                .inProject(entity.getId())
                .save();
    }

    // --- Settings ---

    public Quota getQuota(String slug, AuthContext ctx) {
        var entity = projectRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + slug));
        permissionService.requireProjectRole(ctx, entity.getId(), MemberRole.VIEWER);
        return entity.getQuota() != null ? entity.getQuota() : new Quota();
    }

    @Transactional
    public Quota setQuota(String slug, Quota quota, AuthContext ctx) {
        var entity = projectRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + slug));
        permissionService.requireProjectRole(ctx, entity.getId(), MemberRole.ADMIN);
        entity.setQuota(quota);
        projectRepository.save(entity);
        auditLogService
                .audit(ctx, AuditAction.PROJECT_QUOTA_UPDATED)
                .target(AuditTargetType.PROJECT, entity.getSlug())
                .inWorkspace(entity.getWorkspaceId())
                .inProject(entity.getId())
                .save();
        return quota;
    }

    @Transactional
    public Project setRegistry(String slug, RegistryConfig registryConfig, AuthContext ctx) {
        var entity = projectRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + slug));
        permissionService.requireProjectRole(ctx, entity.getId(), MemberRole.ADMIN);
        entity.setRegistry(registryConfig);
        projectRepository.save(entity);
        auditLogService
                .audit(ctx, AuditAction.PROJECT_REGISTRY_UPDATED)
                .target(AuditTargetType.PROJECT, entity.getSlug())
                .inWorkspace(entity.getWorkspaceId())
                .inProject(entity.getId())
                .save();
        String workspaceSlug = workspaceRepository
                .findById(entity.getWorkspaceId())
                .map(WorkspaceEntity::getSlug)
                .orElse(null);
        return EntityMapper.toApi(entity, workspaceSlug);
    }

    private PagedProjectResponse toPagedResponse(Page<ProjectEntity> page, String workspaceSlug) {
        var response = new PagedProjectResponse();
        response.setContent(page.getContent().stream()
                .map(e -> EntityMapper.toApi(e, workspaceSlug))
                .collect(Collectors.toList()));
        response.setPage(page.getNumber());
        response.setSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        return response;
    }
}

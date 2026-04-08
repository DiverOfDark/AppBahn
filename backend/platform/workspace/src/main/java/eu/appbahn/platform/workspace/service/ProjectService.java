package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.api.model.CreateProjectRequest;
import eu.appbahn.platform.api.model.PagedProjectResponse;
import eu.appbahn.platform.api.model.Project;
import eu.appbahn.platform.api.model.UpdateProjectRequest;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.ConflictException;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.common.util.PaginationUtil;
import eu.appbahn.platform.workspace.entity.ProjectEntity;
import eu.appbahn.platform.workspace.entity.WorkspaceEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.platform.workspace.repository.ProjectRepository;
import eu.appbahn.platform.workspace.repository.WorkspaceRepository;
import eu.appbahn.shared.model.MemberRole;
import eu.appbahn.shared.util.SlugGenerator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final EnvironmentRepository environmentRepository;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    public ProjectService(
            ProjectRepository projectRepository,
            WorkspaceRepository workspaceRepository,
            EnvironmentRepository environmentRepository,
            PermissionService permissionService,
            AuditLogService auditLogService) {
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.environmentRepository = environmentRepository;
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

        auditLogService.log(
                ctx,
                "project.created",
                "project",
                entity.getSlug(),
                workspace.getId(),
                Map.of("name", Map.of("old", (Object) "", "new", entity.getName())));

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

        auditLogService.log(
                ctx,
                "project.updated",
                "project",
                entity.getSlug(),
                entity.getWorkspaceId(),
                Map.of("name", Map.of("old", oldName, "new", entity.getName())));

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

        auditLogService.log(ctx, "project.deleted", "project", entity.getSlug(), entity.getWorkspaceId(), null);
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

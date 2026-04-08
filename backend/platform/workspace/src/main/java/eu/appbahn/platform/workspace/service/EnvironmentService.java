package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.api.model.CreateEnvironmentRequest;
import eu.appbahn.platform.api.model.Environment;
import eu.appbahn.platform.api.model.PagedEnvironmentResponse;
import eu.appbahn.platform.api.model.UpdateEnvironmentRequest;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.common.util.PaginationUtil;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.entity.ProjectEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.platform.workspace.repository.ProjectRepository;
import eu.appbahn.shared.model.MemberRole;
import eu.appbahn.shared.util.SlugGenerator;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final PermissionService permissionService;
    private final NamespaceService namespaceService;
    private final AuditLogService auditLogService;

    public EnvironmentService(
            EnvironmentRepository environmentRepository,
            ProjectRepository projectRepository,
            PermissionService permissionService,
            NamespaceService namespaceService,
            AuditLogService auditLogService) {
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
        this.permissionService = permissionService;
        this.namespaceService = namespaceService;
        this.auditLogService = auditLogService;
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

        // Create Kubernetes namespace
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

        // Delete Kubernetes namespace (cascades all resources)
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

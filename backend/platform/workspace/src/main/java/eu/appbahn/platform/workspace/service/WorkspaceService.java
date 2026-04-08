package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.api.model.CreateWorkspaceRequest;
import eu.appbahn.platform.api.model.PagedWorkspaceResponse;
import eu.appbahn.platform.api.model.UpdateWorkspaceRequest;
import eu.appbahn.platform.api.model.Workspace;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.ConflictException;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.common.util.PaginationUtil;
import eu.appbahn.platform.workspace.entity.WorkspaceEntity;
import eu.appbahn.platform.workspace.entity.WorkspaceMemberEntity;
import eu.appbahn.platform.workspace.repository.ProjectRepository;
import eu.appbahn.platform.workspace.repository.WorkspaceMemberRepository;
import eu.appbahn.platform.workspace.repository.WorkspaceRepository;
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
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository,
            ProjectRepository projectRepository,
            PermissionService permissionService,
            AuditLogService auditLogService) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Workspace create(CreateWorkspaceRequest req, AuthContext ctx) {
        var entity = new WorkspaceEntity();
        entity.setName(req.getName());
        entity.setSlug(SlugGenerator.generate(req.getName()));
        workspaceRepository.save(entity);

        // Creator becomes OWNER
        var member = new WorkspaceMemberEntity();
        member.setWorkspaceId(entity.getId());
        member.setUserId(ctx.userId());
        member.setRole(MemberRole.OWNER.name());
        memberRepository.save(member);

        auditLogService.log(
                ctx,
                "workspace.created",
                "workspace",
                entity.getSlug(),
                entity.getId(),
                Map.of("name", Map.of("old", (Object) "", "new", entity.getName())));

        return EntityMapper.toApi(entity);
    }

    public PagedWorkspaceResponse list(Integer page, Integer size, String sort, AuthContext ctx) {
        var pageable = PaginationUtil.toPageable(page, size, sort);

        Page<WorkspaceEntity> result;
        if (ctx.platformAdmin()) {
            result = workspaceRepository.findAll(pageable);
        } else {
            // Get workspace IDs from direct memberships
            List<UUID> workspaceIds = memberRepository.findByUserId(ctx.userId()).stream()
                    .map(WorkspaceMemberEntity::getWorkspaceId)
                    .collect(Collectors.toList());

            if (workspaceIds.isEmpty()) {
                return emptyPage(page, size);
            }
            result = workspaceRepository.findAllByIdIn(workspaceIds, pageable);
        }

        return toPagedResponse(result);
    }

    public Workspace getBySlug(String slug, AuthContext ctx) {
        var entity = workspaceRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Workspace not found: " + slug));
        permissionService.requireWorkspaceRole(ctx, entity.getId(), MemberRole.VIEWER);
        return EntityMapper.toApi(entity);
    }

    @Transactional
    public Workspace update(String slug, UpdateWorkspaceRequest req, AuthContext ctx) {
        var entity = workspaceRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Workspace not found: " + slug));
        permissionService.requireWorkspaceRole(ctx, entity.getId(), MemberRole.ADMIN);

        String oldName = entity.getName();
        if (req.getName() != null) {
            entity.setName(req.getName());
        }
        workspaceRepository.save(entity);

        auditLogService.log(
                ctx,
                "workspace.updated",
                "workspace",
                entity.getSlug(),
                entity.getId(),
                Map.of("name", Map.of("old", (Object) oldName, "new", entity.getName())));

        return EntityMapper.toApi(entity);
    }

    @Transactional
    public void delete(String slug, AuthContext ctx) {
        var entity = workspaceRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Workspace not found: " + slug));
        permissionService.requireWorkspaceRole(ctx, entity.getId(), MemberRole.OWNER);

        // Block if projects exist
        var projects = projectRepository.findByWorkspaceId(entity.getId());
        if (!projects.isEmpty()) {
            List<String> projectSlugs = projects.stream().map(p -> p.getSlug()).collect(Collectors.toList());
            throw new ConflictException("Cannot delete workspace with existing projects", projectSlugs);
        }

        workspaceRepository.delete(entity);

        auditLogService.log(ctx, "workspace.deleted", "workspace", entity.getSlug(), entity.getId(), null);
    }

    private PagedWorkspaceResponse toPagedResponse(Page<WorkspaceEntity> page) {
        var response = new PagedWorkspaceResponse();
        response.setContent(page.getContent().stream().map(EntityMapper::toApi).collect(Collectors.toList()));
        response.setPage(page.getNumber());
        response.setSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        return response;
    }

    private PagedWorkspaceResponse emptyPage(Integer page, Integer size) {
        var response = new PagedWorkspaceResponse();
        response.setContent(List.of());
        response.setPage(page != null ? page : 0);
        response.setSize(size != null ? size : 20);
        response.setTotalElements(0L);
        response.setTotalPages(0);
        return response;
    }
}

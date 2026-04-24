package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.api.model.AuditAction;
import eu.appbahn.platform.api.model.AuditTargetType;
import eu.appbahn.platform.api.model.CreateGroupMappingRequest;
import eu.appbahn.platform.api.model.OidcGroupMapping;
import eu.appbahn.platform.api.model.UpdateGroupMappingRequest;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.workspace.entity.OidcGroupMappingEntity;
import eu.appbahn.platform.workspace.entity.WorkspaceEntity;
import eu.appbahn.platform.workspace.repository.OidcGroupMappingRepository;
import eu.appbahn.platform.workspace.repository.WorkspaceRepository;
import eu.appbahn.shared.model.MemberRole;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupMappingService {

    private final WorkspaceRepository workspaceRepository;
    private final OidcGroupMappingRepository mappingRepository;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    public GroupMappingService(
            WorkspaceRepository workspaceRepository,
            OidcGroupMappingRepository mappingRepository,
            PermissionService permissionService,
            AuditLogService auditLogService) {
        this.workspaceRepository = workspaceRepository;
        this.mappingRepository = mappingRepository;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    public List<OidcGroupMapping> list(String slug, AuthContext ctx) {
        var ws = findWorkspace(slug);
        permissionService.requireWorkspaceRole(ctx, ws.getId(), MemberRole.ADMIN);
        return mappingRepository.findByWorkspaceId(ws.getId()).stream()
                .map(this::toApi)
                .collect(Collectors.toList());
    }

    @Transactional
    public OidcGroupMapping create(String slug, CreateGroupMappingRequest req, AuthContext ctx) {
        var ws = findWorkspace(slug);
        permissionService.requireWorkspaceRole(ctx, ws.getId(), MemberRole.ADMIN);

        var entity = new OidcGroupMappingEntity();
        entity.setWorkspaceId(ws.getId());
        entity.setOidcGroup(req.getOidcGroup());
        entity.setRole(req.getRole().getValue());
        mappingRepository.save(entity);

        auditLogService
                .audit(ctx, AuditAction.GROUP_MAPPING_CREATED)
                .target(AuditTargetType.WORKSPACE, ws.getSlug())
                .inWorkspace(ws.getId())
                .detail("oidcGroup", req.getOidcGroup())
                .detail("role", req.getRole().getValue())
                .save();

        return toApi(entity);
    }

    @Transactional
    public OidcGroupMapping update(String slug, UUID mappingId, UpdateGroupMappingRequest req, AuthContext ctx) {
        var ws = findWorkspace(slug);
        permissionService.requireWorkspaceRole(ctx, ws.getId(), MemberRole.ADMIN);

        var entity = mappingRepository
                .findById(mappingId)
                .orElseThrow(() -> new NotFoundException("Group mapping not found"));

        if (req.getRole() != null) {
            entity.setRole(req.getRole().getValue());
        }
        mappingRepository.save(entity);

        auditLogService
                .audit(ctx, AuditAction.GROUP_MAPPING_UPDATED)
                .target(AuditTargetType.WORKSPACE, ws.getSlug())
                .inWorkspace(ws.getId())
                .detail("mappingId", mappingId.toString())
                .save();

        return toApi(entity);
    }

    @Transactional
    public void delete(String slug, UUID mappingId, AuthContext ctx) {
        var ws = findWorkspace(slug);
        permissionService.requireWorkspaceRole(ctx, ws.getId(), MemberRole.ADMIN);

        var entity = mappingRepository
                .findById(mappingId)
                .orElseThrow(() -> new NotFoundException("Group mapping not found"));
        mappingRepository.delete(entity);

        auditLogService
                .audit(ctx, AuditAction.GROUP_MAPPING_DELETED)
                .target(AuditTargetType.WORKSPACE, ws.getSlug())
                .inWorkspace(ws.getId())
                .save();
    }

    private OidcGroupMapping toApi(OidcGroupMappingEntity entity) {
        var dto = new OidcGroupMapping();
        dto.setId(entity.getId());
        dto.setWorkspaceId(entity.getWorkspaceId());
        dto.setOidcGroup(entity.getOidcGroup());
        dto.setRole(OidcGroupMapping.RoleEnum.fromValue(entity.getRole()));
        return dto;
    }

    private WorkspaceEntity findWorkspace(String slug) {
        return workspaceRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Workspace not found: " + slug));
    }
}

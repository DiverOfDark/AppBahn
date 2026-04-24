package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.api.model.AddMemberRequest;
import eu.appbahn.platform.api.model.AddMemberResponse;
import eu.appbahn.platform.api.model.AuditAction;
import eu.appbahn.platform.api.model.AuditTargetType;
import eu.appbahn.platform.api.model.UpdateMemberRequest;
import eu.appbahn.platform.api.model.WorkspaceMember;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.ConflictException;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.user.entity.UserEntity;
import eu.appbahn.platform.user.repository.UserRepository;
import eu.appbahn.platform.workspace.entity.PendingInvitationEntity;
import eu.appbahn.platform.workspace.entity.WorkspaceEntity;
import eu.appbahn.platform.workspace.entity.WorkspaceMemberEntity;
import eu.appbahn.platform.workspace.repository.PendingInvitationRepository;
import eu.appbahn.platform.workspace.repository.WorkspaceMemberRepository;
import eu.appbahn.platform.workspace.repository.WorkspaceRepository;
import eu.appbahn.shared.model.MemberRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final PendingInvitationRepository pendingInvitationRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    public MemberService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository,
            PendingInvitationRepository pendingInvitationRepository,
            UserRepository userRepository,
            PermissionService permissionService,
            AuditLogService auditLogService) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.pendingInvitationRepository = pendingInvitationRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    public List<WorkspaceMember> listMembers(String slug, AuthContext ctx) {
        var ws = findWorkspace(slug);
        permissionService.requireWorkspaceRole(ctx, ws.getId(), MemberRole.VIEWER);

        var members = memberRepository.findByWorkspaceId(ws.getId());
        var userIds = members.stream().map(WorkspaceMemberEntity::getUserId).collect(Collectors.toList());
        var usersById =
                userRepository.findAllById(userIds).stream().collect(Collectors.toMap(UserEntity::getId, u -> u));

        var result = new ArrayList<WorkspaceMember>();
        for (var m : members) {
            var dto = new WorkspaceMember();
            dto.setUserId(m.getUserId());
            dto.setRole(WorkspaceMember.RoleEnum.fromValue(m.getRole()));
            dto.setStatus(WorkspaceMember.StatusEnum.ACTIVE);
            var user = usersById.get(m.getUserId());
            if (user != null) {
                dto.setEmail(user.getEmail());
            }
            result.add(dto);
        }

        var pending = pendingInvitationRepository.findByWorkspaceId(ws.getId());
        for (var inv : pending) {
            var dto = new WorkspaceMember();
            dto.setEmail(inv.getEmail());
            dto.setRole(WorkspaceMember.RoleEnum.fromValue(inv.getRole()));
            dto.setStatus(WorkspaceMember.StatusEnum.PENDING);
            result.add(dto);
        }

        return result;
    }

    @Transactional
    public AddMemberResponse addMember(String slug, AddMemberRequest req, AuthContext ctx) {
        var ws = findWorkspace(slug);
        permissionService.requireWorkspaceRole(ctx, ws.getId(), MemberRole.ADMIN);

        var existingPending = pendingInvitationRepository.findByWorkspaceIdAndEmail(ws.getId(), req.getEmail());
        if (existingPending.isPresent()) {
            throw new ConflictException("Invitation already pending for this email", List.of(req.getEmail()));
        }

        UserEntity user = userRepository.findByEmail(req.getEmail()).orElse(null);

        if (user != null) {
            var existing = memberRepository.findByWorkspaceIdAndUserId(ws.getId(), user.getId());
            if (existing.isPresent()) {
                throw new ConflictException("User is already a member", List.of(req.getEmail()));
            }
            var member = new WorkspaceMemberEntity();
            member.setWorkspaceId(ws.getId());
            member.setUserId(user.getId());
            member.setRole(req.getRole().getValue());
            memberRepository.save(member);

            auditLogService
                    .audit(ctx, AuditAction.MEMBER_ADDED)
                    .target(AuditTargetType.WORKSPACE, ws.getSlug())
                    .inWorkspace(ws.getId())
                    .detail("email", req.getEmail())
                    .detail("role", req.getRole().getValue())
                    .save();

            var resp = new AddMemberResponse();
            resp.setStatus(AddMemberResponse.StatusEnum.ACTIVE);
            return resp;
        } else {
            var invitation = new PendingInvitationEntity();
            invitation.setWorkspaceId(ws.getId());
            invitation.setEmail(req.getEmail());
            invitation.setRole(req.getRole().getValue());
            invitation.setInvitedBy(ctx.userId());
            pendingInvitationRepository.save(invitation);

            auditLogService
                    .audit(ctx, AuditAction.MEMBER_INVITED)
                    .target(AuditTargetType.WORKSPACE, ws.getSlug())
                    .inWorkspace(ws.getId())
                    .detail("email", req.getEmail())
                    .detail("role", req.getRole().getValue())
                    .save();

            var resp = new AddMemberResponse();
            resp.setStatus(AddMemberResponse.StatusEnum.PENDING);
            return resp;
        }
    }

    @Transactional
    public WorkspaceMember updateMember(String slug, UUID userId, UpdateMemberRequest req, AuthContext ctx) {
        var ws = findWorkspace(slug);
        permissionService.requireWorkspaceRole(ctx, ws.getId(), MemberRole.ADMIN);

        var member = memberRepository
                .findByWorkspaceIdAndUserId(ws.getId(), userId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        String oldRole = member.getRole();
        member.setRole(req.getRole().getValue());
        memberRepository.save(member);

        auditLogService
                .audit(ctx, AuditAction.MEMBER_UPDATED)
                .target(AuditTargetType.WORKSPACE, ws.getSlug())
                .inWorkspace(ws.getId())
                .change("role", oldRole, req.getRole().getValue())
                .save();

        var dto = new WorkspaceMember();
        dto.setUserId(userId);
        dto.setRole(WorkspaceMember.RoleEnum.fromValue(member.getRole()));
        userRepository.findById(userId).ifPresent(u -> dto.setEmail(u.getEmail()));
        return dto;
    }

    @Transactional
    public void removeMember(String slug, UUID userId, AuthContext ctx) {
        var ws = findWorkspace(slug);
        permissionService.requireWorkspaceRole(ctx, ws.getId(), MemberRole.ADMIN);

        var member = memberRepository
                .findByWorkspaceIdAndUserId(ws.getId(), userId)
                .orElseThrow(() -> new NotFoundException("Member not found"));
        memberRepository.delete(member);

        auditLogService
                .audit(ctx, AuditAction.MEMBER_REMOVED)
                .target(AuditTargetType.WORKSPACE, ws.getSlug())
                .inWorkspace(ws.getId())
                .save();
    }

    private WorkspaceEntity findWorkspace(String slug) {
        return workspaceRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Workspace not found: " + slug));
    }
}

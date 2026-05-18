package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.api.AuditAction;
import eu.appbahn.platform.api.AuditTargetType;
import eu.appbahn.platform.api.MemberStatus;
import eu.appbahn.platform.api.UpdateMemberRequest;
import eu.appbahn.platform.api.WorkspaceMember;
import eu.appbahn.platform.api.workspace.AddMemberRequest;
import eu.appbahn.platform.api.workspace.AddMemberResponse;
import eu.appbahn.platform.api.workspace.WorkspaceMemberSample;
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
import java.util.Comparator;
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
            result.add(toActiveMember(m, usersById.get(m.getUserId())));
        }

        var pending = pendingInvitationRepository.findByWorkspaceId(ws.getId());
        for (var inv : pending) {
            var dto = new WorkspaceMember();
            dto.setEmail(inv.getEmail());
            dto.setRole(MemberRole.valueOf(inv.getRole()));
            dto.setStatus(MemberStatus.PENDING);
            result.add(dto);
        }

        return result;
    }

    public static final int SAMPLE_LIMIT_DEFAULT = 4;
    public static final int SAMPLE_LIMIT_MAX = 10;

    /**
     * One DB hit per repository call (workspaces, members, users) regardless of input size — the
     * console can render N workspace cards with N+2 queries instead of 2N+1.
     *
     * <p>Workspaces the caller has no role on are dropped from the result rather than 403-ing; the
     * caller already proved access by listing them on the previous page, so missing entries are
     * the right signal for "do not render avatars on this card".
     */
    public List<WorkspaceMemberSample> sampleMembers(List<String> slugs, int limit, AuthContext ctx) {
        if (slugs == null || slugs.isEmpty()) {
            return List.of();
        }
        int effectiveLimit = Math.min(Math.max(limit, 1), SAMPLE_LIMIT_MAX);

        var workspaces = workspaceRepository.findAllBySlugIn(slugs);
        var allowedWorkspaces = workspaces.stream()
                .filter(ws -> ctx.platformAdmin() || permissionService.resolveWorkspaceRole(ctx, ws.getId()) != null)
                .toList();
        if (allowedWorkspaces.isEmpty()) {
            return List.of();
        }

        var workspaceIds =
                allowedWorkspaces.stream().map(WorkspaceEntity::getId).toList();
        var membersByWorkspace = memberRepository.findByWorkspaceIdIn(workspaceIds).stream()
                .collect(Collectors.groupingBy(WorkspaceMemberEntity::getWorkspaceId));
        var allUserIds = membersByWorkspace.values().stream()
                .flatMap(List::stream)
                .map(WorkspaceMemberEntity::getUserId)
                .collect(Collectors.toSet());
        var usersById =
                userRepository.findAllById(allUserIds).stream().collect(Collectors.toMap(UserEntity::getId, u -> u));

        var result = new ArrayList<WorkspaceMemberSample>();
        for (var ws : allowedWorkspaces) {
            var wsMembers = membersByWorkspace.getOrDefault(ws.getId(), List.of());
            var sample = new WorkspaceMemberSample();
            sample.setSlug(ws.getSlug());
            sample.setTotalCount(wsMembers.size());
            wsMembers.stream()
                    .sorted(Comparator.comparing(
                            WorkspaceMemberEntity::getUserId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .limit(effectiveLimit)
                    .map(m -> toActiveMember(m, usersById.get(m.getUserId())))
                    .forEach(sample.getMembers()::add);
            result.add(sample);
        }
        return result;
    }

    private WorkspaceMember toActiveMember(WorkspaceMemberEntity entity, UserEntity user) {
        var dto = new WorkspaceMember();
        dto.setUserId(entity.getUserId());
        dto.setRole(MemberRole.valueOf(entity.getRole()));
        dto.setStatus(MemberStatus.ACTIVE);
        if (user != null) {
            dto.setEmail(user.getEmail());
            dto.setName(user.getName());
            dto.setAvatarUrl(user.getAvatarUrl());
        }
        return dto;
    }

    /**
     * Adding a member always lands in {@link MemberStatus#PENDING}. The invitee accepts via
     * {@code InviteService.acceptInvite} (existing users, in their console) or via the
     * auto-conversion path triggered when a brand-new email first authenticates and a
     * {@code UserEntity} is provisioned. Adding an existing user directly to active membership
     * would join them to a workspace without consent.
     */
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
        }

        var invitation = new PendingInvitationEntity();
        invitation.setWorkspaceId(ws.getId());
        invitation.setEmail(req.getEmail());
        invitation.setRole(req.getRole().name());
        invitation.setInvitedBy(ctx.userId());
        pendingInvitationRepository.save(invitation);

        auditLogService
                .audit(ctx, AuditAction.MEMBER_INVITED)
                .target(AuditTargetType.WORKSPACE, ws.getSlug())
                .inWorkspace(ws.getId())
                .detail("email", req.getEmail())
                .detail("role", req.getRole().name())
                .save();

        var resp = new AddMemberResponse();
        resp.setStatus(MemberStatus.PENDING);
        return resp;
    }

    @Transactional
    public WorkspaceMember updateMember(String slug, UUID userId, UpdateMemberRequest req, AuthContext ctx) {
        var ws = findWorkspace(slug);
        permissionService.requireWorkspaceRole(ctx, ws.getId(), MemberRole.ADMIN);

        var member = memberRepository
                .findByWorkspaceIdAndUserId(ws.getId(), userId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        String oldRole = member.getRole();
        member.setRole(req.getRole().name());
        memberRepository.save(member);

        auditLogService
                .audit(ctx, AuditAction.MEMBER_UPDATED)
                .target(AuditTargetType.WORKSPACE, ws.getSlug())
                .inWorkspace(ws.getId())
                .change("role", oldRole, req.getRole().name())
                .save();

        var user = userRepository.findById(userId).orElse(null);
        var dto = toActiveMember(member, user);
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

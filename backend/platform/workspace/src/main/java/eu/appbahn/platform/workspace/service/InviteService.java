package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.api.AuditAction;
import eu.appbahn.platform.api.AuditTargetType;
import eu.appbahn.platform.api.invite.CreateInviteCodeRequest;
import eu.appbahn.platform.api.invite.RedeemInviteRequest;
import eu.appbahn.platform.api.invite.WorkspaceInvite;
import eu.appbahn.platform.api.invite.WorkspaceInviteCode;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.ConflictException;
import eu.appbahn.platform.common.exception.ForbiddenException;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.exception.ValidationException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.user.repository.UserRepository;
import eu.appbahn.platform.workspace.entity.InviteCodeEntity;
import eu.appbahn.platform.workspace.entity.PendingInvitationEntity;
import eu.appbahn.platform.workspace.entity.WorkspaceEntity;
import eu.appbahn.platform.workspace.entity.WorkspaceMemberEntity;
import eu.appbahn.platform.workspace.repository.InviteCodeRepository;
import eu.appbahn.platform.workspace.repository.PendingInvitationRepository;
import eu.appbahn.platform.workspace.repository.WorkspaceMemberRepository;
import eu.appbahn.platform.workspace.repository.WorkspaceRepository;
import eu.appbahn.shared.model.MemberRole;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InviteService {

    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz";
    private static final int CODE_RANDOM_LENGTH = 12;
    private static final String CODE_PREFIX = "abp_";

    private final PendingInvitationRepository invitationRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    public InviteService(
            PendingInvitationRepository invitationRepository,
            InviteCodeRepository inviteCodeRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository,
            UserRepository userRepository,
            PermissionService permissionService,
            AuditLogService auditLogService) {
        this.invitationRepository = invitationRepository;
        this.inviteCodeRepository = inviteCodeRepository;
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    public List<WorkspaceInvite> listMyInvites(AuthContext ctx) {
        String email = ctx.email();
        if (email == null) {
            return List.of();
        }
        Instant now = Instant.now();
        return invitationRepository.findByEmail(email).stream()
                .filter(inv -> inv.getExpiresAt() == null || inv.getExpiresAt().isAfter(now))
                .map(inv -> toInviteDto(inv))
                .collect(Collectors.toList());
    }

    @Transactional
    public WorkspaceInvite acceptInvite(UUID inviteId, AuthContext ctx) {
        var inv = findInviteForCaller(inviteId, ctx);
        var workspace = workspaceRepository
                .findById(inv.getWorkspaceId())
                .orElseThrow(() -> new NotFoundException("Workspace not found"));

        var existing = memberRepository.findByWorkspaceIdAndUserId(workspace.getId(), ctx.userId());
        if (existing.isPresent()) {
            throw new ConflictException("Already a member of this workspace", List.of());
        }

        var member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspace.getId());
        member.setUserId(ctx.userId());
        member.setRole(inv.getRole());
        memberRepository.save(member);

        var result = toInviteDto(inv);
        invitationRepository.delete(inv);

        auditLogService
                .audit(ctx, AuditAction.INVITE_ACCEPTED)
                .target(AuditTargetType.WORKSPACE, workspace.getSlug())
                .inWorkspace(workspace.getId())
                .detail("role", inv.getRole())
                .save();

        return result;
    }

    @Transactional
    public void declineInvite(UUID inviteId, AuthContext ctx) {
        var inv = findInviteForCaller(inviteId, ctx);
        var workspace = workspaceRepository.findById(inv.getWorkspaceId()).orElse(null);
        invitationRepository.delete(inv);

        if (workspace != null) {
            auditLogService
                    .audit(ctx, AuditAction.INVITE_DECLINED)
                    .target(AuditTargetType.WORKSPACE, workspace.getSlug())
                    .inWorkspace(workspace.getId())
                    .save();
        }
    }

    @Transactional
    public WorkspaceInvite redeemCode(RedeemInviteRequest req, AuthContext ctx) {
        var codeEntity = inviteCodeRepository
                .findByCode(req.getCode())
                .orElseThrow(() -> new NotFoundException("Invite code not found or invalid"));

        Instant now = Instant.now();
        if (codeEntity.getExpiresAt() != null && codeEntity.getExpiresAt().isBefore(now)) {
            throw new ValidationException("Invite code has expired");
        }
        if (codeEntity.getUseCount() >= codeEntity.getMaxUses()) {
            throw new ValidationException("Invite code has been fully redeemed");
        }

        var workspace = workspaceRepository
                .findById(codeEntity.getWorkspaceId())
                .orElseThrow(() -> new NotFoundException("Workspace not found"));

        var existing = memberRepository.findByWorkspaceIdAndUserId(workspace.getId(), ctx.userId());
        if (existing.isPresent()) {
            throw new ConflictException("Already a member of this workspace", List.of());
        }

        var member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspace.getId());
        member.setUserId(ctx.userId());
        member.setRole(codeEntity.getRole());
        memberRepository.save(member);

        codeEntity.setUseCount(codeEntity.getUseCount() + 1);
        if (codeEntity.getMaxUses() == 1) {
            codeEntity.setRedeemedBy(ctx.userId());
            codeEntity.setRedeemedAt(now);
        }
        inviteCodeRepository.save(codeEntity);

        auditLogService
                .audit(ctx, AuditAction.INVITE_REDEEMED)
                .target(AuditTargetType.WORKSPACE, workspace.getSlug())
                .inWorkspace(workspace.getId())
                .detail("role", codeEntity.getRole())
                .save();

        var invite = new WorkspaceInvite();
        invite.setId(codeEntity.getId());
        invite.setWorkspaceSlug(workspace.getSlug());
        invite.setWorkspaceName(workspace.getName());
        invite.setRole(MemberRole.valueOf(codeEntity.getRole()));
        invite.setInvitedAt(codeEntity.getCreatedAt());
        invite.setExpiresAt(codeEntity.getExpiresAt());
        return invite;
    }

    @Transactional
    public WorkspaceInviteCode createInviteCode(String wsSlug, CreateInviteCodeRequest req, AuthContext ctx) {
        var workspace = findWorkspace(wsSlug);
        permissionService.requireWorkspaceRole(ctx, workspace.getId(), MemberRole.OWNER);

        var entity = new InviteCodeEntity();
        entity.setWorkspaceId(workspace.getId());
        entity.setCode(generateCode());
        entity.setRole(req.getRole().name());
        entity.setCreatedBy(ctx.userId());
        entity.setExpiresAt(req.getExpiresAt());
        entity.setMaxUses(req.getMaxUses() > 0 ? req.getMaxUses() : 1);
        entity.setUseCount(0);
        inviteCodeRepository.save(entity);

        auditLogService
                .audit(ctx, AuditAction.INVITE_CODE_CREATED)
                .target(AuditTargetType.WORKSPACE, workspace.getSlug())
                .inWorkspace(workspace.getId())
                .detail("role", req.getRole().name())
                .save();

        return toCodeDto(entity);
    }

    public List<WorkspaceInviteCode> listInviteCodes(String wsSlug, AuthContext ctx) {
        var workspace = findWorkspace(wsSlug);
        permissionService.requireWorkspaceRole(ctx, workspace.getId(), MemberRole.OWNER);
        return inviteCodeRepository.findByWorkspaceId(workspace.getId()).stream()
                .map(this::toCodeDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void revokeInviteCode(String wsSlug, UUID codeId, AuthContext ctx) {
        var workspace = findWorkspace(wsSlug);
        permissionService.requireWorkspaceRole(ctx, workspace.getId(), MemberRole.OWNER);

        var entity =
                inviteCodeRepository.findById(codeId).orElseThrow(() -> new NotFoundException("Invite code not found"));
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new NotFoundException("Invite code not found");
        }
        inviteCodeRepository.delete(entity);

        auditLogService
                .audit(ctx, AuditAction.INVITE_CODE_REVOKED)
                .target(AuditTargetType.WORKSPACE, workspace.getSlug())
                .inWorkspace(workspace.getId())
                .save();
    }

    private PendingInvitationEntity findInviteForCaller(UUID inviteId, AuthContext ctx) {
        var inv = invitationRepository
                .findById(inviteId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));
        String callerEmail = ctx.email();
        if (callerEmail == null || !callerEmail.equalsIgnoreCase(inv.getEmail())) {
            throw new ForbiddenException("This invitation is not addressed to you");
        }
        Instant now = Instant.now();
        if (inv.getExpiresAt() != null && inv.getExpiresAt().isBefore(now)) {
            throw new ValidationException("This invitation has expired");
        }
        return inv;
    }

    private WorkspaceInvite toInviteDto(PendingInvitationEntity inv) {
        var workspace = workspaceRepository.findById(inv.getWorkspaceId()).orElse(null);
        var dto = new WorkspaceInvite();
        dto.setId(inv.getId());
        dto.setWorkspaceSlug(
                workspace != null ? workspace.getSlug() : inv.getWorkspaceId().toString());
        dto.setWorkspaceName(workspace != null ? workspace.getName() : "");
        dto.setRole(MemberRole.valueOf(inv.getRole()));
        dto.setInvitedAt(inv.getCreatedAt());
        dto.setExpiresAt(inv.getExpiresAt());
        if (inv.getInvitedBy() != null) {
            userRepository.findById(inv.getInvitedBy()).ifPresent(u -> {
                var invitedBy = new WorkspaceInvite.InvitedBy();
                invitedBy.setId(u.getId());
                invitedBy.setName(u.getName() != null ? u.getName() : u.getEmail());
                invitedBy.setEmail(u.getEmail());
                dto.setInvitedBy(invitedBy);
            });
        }
        return dto;
    }

    private WorkspaceInviteCode toCodeDto(InviteCodeEntity entity) {
        var dto = new WorkspaceInviteCode();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setRole(MemberRole.valueOf(entity.getRole()));
        dto.setExpiresAt(entity.getExpiresAt());
        dto.setMaxUses(entity.getMaxUses());
        dto.setUseCount(entity.getUseCount());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        return dto;
    }

    private String generateCode() {
        var sb = new StringBuilder(CODE_PREFIX);
        for (int i = 0; i < CODE_RANDOM_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private WorkspaceEntity findWorkspace(String slug) {
        return workspaceRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Workspace not found: " + slug));
    }
}

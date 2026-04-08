package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.common.exception.ForbiddenException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.workspace.entity.ProjectEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentMemberOverrideRepository;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.platform.workspace.repository.OidcGroupMappingRepository;
import eu.appbahn.platform.workspace.repository.ProjectMemberOverrideRepository;
import eu.appbahn.platform.workspace.repository.ProjectRepository;
import eu.appbahn.platform.workspace.repository.WorkspaceMemberRepository;
import eu.appbahn.shared.model.MemberRole;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final WorkspaceMemberRepository memberRepository;
    private final OidcGroupMappingRepository groupMappingRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectMemberOverrideRepository projectOverrideRepository;
    private final EnvironmentMemberOverrideRepository envOverrideRepository;

    public PermissionService(
            WorkspaceMemberRepository memberRepository,
            OidcGroupMappingRepository groupMappingRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            ProjectMemberOverrideRepository projectOverrideRepository,
            EnvironmentMemberOverrideRepository envOverrideRepository) {
        this.memberRepository = memberRepository;
        this.groupMappingRepository = groupMappingRepository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.projectOverrideRepository = projectOverrideRepository;
        this.envOverrideRepository = envOverrideRepository;
    }

    public MemberRole resolveWorkspaceRole(AuthContext ctx, UUID workspaceId) {
        if (ctx.platformAdmin()) {
            return MemberRole.OWNER;
        }

        MemberRole highest = null;

        // Direct membership
        var member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, ctx.userId());
        if (member.isPresent()) {
            highest = MemberRole.valueOf(member.get().getRole());
        }

        // OIDC group mappings
        if (!ctx.groups().isEmpty()) {
            var mappings = groupMappingRepository.findByWorkspaceIdAndOidcGroupIn(workspaceId, ctx.groups());
            for (var mapping : mappings) {
                MemberRole role = MemberRole.valueOf(mapping.getRole());
                if (highest == null || role.ordinal() < highest.ordinal()) {
                    highest = role;
                }
            }
        }

        return highest;
    }

    public MemberRole resolveProjectRole(AuthContext ctx, UUID projectId) {
        ProjectEntity project = projectRepository.findById(projectId).orElse(null);
        if (project == null) return null;

        MemberRole inherited = resolveWorkspaceRole(ctx, project.getWorkspaceId());

        // Check project override — effective = max(inherited, override)
        var override = projectOverrideRepository.findByProjectIdAndUserId(projectId, ctx.userId());
        if (override.isPresent()) {
            MemberRole overrideRole = MemberRole.valueOf(override.get().getRole());
            if (inherited == null || overrideRole.ordinal() < inherited.ordinal()) {
                return overrideRole;
            }
        }

        return inherited;
    }

    public MemberRole resolveEnvironmentRole(AuthContext ctx, UUID environmentId) {
        var env = environmentRepository.findById(environmentId).orElse(null);
        if (env == null) return null;

        MemberRole inherited = resolveProjectRole(ctx, env.getProjectId());

        // Check environment override — effective = max(inherited, override)
        var override = envOverrideRepository.findByEnvironmentIdAndUserId(environmentId, ctx.userId());
        if (override.isPresent()) {
            MemberRole overrideRole = MemberRole.valueOf(override.get().getRole());
            if (inherited == null || overrideRole.ordinal() < inherited.ordinal()) {
                return overrideRole;
            }
        }

        return inherited;
    }

    public void requireWorkspaceRole(AuthContext ctx, UUID workspaceId, MemberRole minimum) {
        MemberRole role = resolveWorkspaceRole(ctx, workspaceId);
        if (role == null || role.ordinal() > minimum.ordinal()) {
            throw new ForbiddenException("Insufficient permissions on workspace");
        }
    }

    public void requireProjectRole(AuthContext ctx, UUID projectId, MemberRole minimum) {
        MemberRole role = resolveProjectRole(ctx, projectId);
        if (role == null || role.ordinal() > minimum.ordinal()) {
            throw new ForbiddenException("Insufficient permissions on project");
        }
    }

    public void requireEnvironmentRole(AuthContext ctx, UUID environmentId, MemberRole minimum) {
        MemberRole role = resolveEnvironmentRole(ctx, environmentId);
        if (role == null || role.ordinal() > minimum.ordinal()) {
            throw new ForbiddenException("Insufficient permissions on environment");
        }
    }
}

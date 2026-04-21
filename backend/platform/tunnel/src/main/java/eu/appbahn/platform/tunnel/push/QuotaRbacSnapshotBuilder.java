package eu.appbahn.platform.tunnel.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.model.Quota;
import eu.appbahn.platform.common.util.JsonUtil;
import eu.appbahn.platform.resource.repository.ResourceCacheRepository;
import eu.appbahn.platform.user.entity.UserEntity;
import eu.appbahn.platform.user.repository.UserRepository;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.entity.ProjectEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentMemberOverrideRepository;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.platform.workspace.repository.OidcGroupMappingRepository;
import eu.appbahn.platform.workspace.repository.ProjectMemberOverrideRepository;
import eu.appbahn.platform.workspace.repository.ProjectRepository;
import eu.appbahn.platform.workspace.repository.WorkspaceMemberRepository;
import eu.appbahn.platform.workspace.service.NamespaceService;
import eu.appbahn.shared.model.MemberRole;
import eu.appbahn.tunnel.v1.QuotaRbacCachePush;
import eu.appbahn.tunnel.v1.QuotaRbacSnapshot;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produces a {@link QuotaRbacCachePush} frame carrying everything the operator's admission
 * webhook needs to decide allow/deny locally: env↔namespace mappings, per-env resource-count
 * caps, current resource count, and the RBAC closure (OIDC subjects + groups that resolve to
 * {@link MemberRole#EDITOR} or higher on the environment).
 *
 * <p>Revision is content-addressed: identical snapshot bytes on any replica produce the same
 * revision, so the operator dedupes repeated pushes without cross-replica coordination.
 *
 * <p>v1 scope: env-level {@code maxResources} only. CPU/memory/storage/replicas quota
 * dimensions, and project/workspace-level enforcement at the admission webhook, remain on
 * the platform-side REST path (see {@code QuotaService}).
 */
@Service
public class QuotaRbacSnapshotBuilder {

    private static final int EDITOR_ORDINAL = MemberRole.EDITOR.ordinal();

    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final OidcGroupMappingRepository oidcGroupMappingRepository;
    private final ProjectMemberOverrideRepository projectOverrideRepository;
    private final EnvironmentMemberOverrideRepository envOverrideRepository;
    private final ResourceCacheRepository resourceCacheRepository;
    private final UserRepository userRepository;
    private final NamespaceService namespaceService;
    private final ObjectMapper objectMapper;
    private final List<String> platformAdminGroups;

    public QuotaRbacSnapshotBuilder(
            EnvironmentRepository environmentRepository,
            ProjectRepository projectRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            OidcGroupMappingRepository oidcGroupMappingRepository,
            ProjectMemberOverrideRepository projectOverrideRepository,
            EnvironmentMemberOverrideRepository envOverrideRepository,
            ResourceCacheRepository resourceCacheRepository,
            UserRepository userRepository,
            NamespaceService namespaceService,
            ObjectMapper objectMapper,
            @Value("${platform.admin-groups:}") String platformAdminGroups) {
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.oidcGroupMappingRepository = oidcGroupMappingRepository;
        this.projectOverrideRepository = projectOverrideRepository;
        this.envOverrideRepository = envOverrideRepository;
        this.resourceCacheRepository = resourceCacheRepository;
        this.userRepository = userRepository;
        this.namespaceService = namespaceService;
        this.objectMapper = objectMapper;
        this.platformAdminGroups = splitCsv(platformAdminGroups);
    }

    @Transactional(readOnly = true)
    public QuotaRbacCachePush buildFor(String clusterName) {
        var snapshot = QuotaRbacSnapshot.newBuilder();
        platformAdminGroups.forEach(snapshot::addPlatformAdminGroups);

        environmentRepository.findByTargetCluster(clusterName).stream()
                // Sort by slug so proto bytes (and the revision hash) are deterministic
                // across repeated calls and across platform replicas.
                .sorted(Comparator.comparing(EnvironmentEntity::getSlug))
                .forEach(env -> snapshot.addEnvironments(buildEntry(env)));

        QuotaRbacSnapshot built = snapshot.build();
        return QuotaRbacCachePush.newBuilder()
                .setRevision(SnapshotRevisions.contentRevision(built))
                .setSnapshot(built)
                .build();
    }

    private QuotaRbacSnapshot.EnvironmentEntry buildEntry(EnvironmentEntity env) {
        var entry = QuotaRbacSnapshot.EnvironmentEntry.newBuilder()
                .setSlug(env.getSlug())
                .setNamespace(namespaceService.computeNamespace(env.getSlug()))
                .setCurrentResources(
                        (int) Math.min(Integer.MAX_VALUE, resourceCacheRepository.countByEnvironmentId(env.getId())))
                .setMaxResources(parseMaxResources(env.getQuota()));

        ProjectEntity project = projectRepository.findById(env.getProjectId()).orElse(null);
        if (project == null) {
            // Orphan env — no workspace to resolve RBAC from. Fail-closed: no allowed users/groups.
            return entry.build();
        }
        UUID workspaceId = project.getWorkspaceId();

        resolveAllowedSubjects(env.getId(), project.getId(), workspaceId).forEach(entry::addAllowedUserSubjects);
        resolveAllowedGroups(workspaceId).forEach(entry::addAllowedOidcGroups);
        return entry.build();
    }

    /**
     * Builds the set of OIDC subjects resolving to {@code EDITOR+} on {@code environmentId}.
     * Mirrors {@link eu.appbahn.platform.workspace.service.PermissionService#resolveEnvironmentRole}
     * inverted: iterate every user touched by a workspace membership + project/env override,
     * then test each against the same precedence rules. Sorted so the resulting proto bytes
     * are deterministic.
     */
    private List<String> resolveAllowedSubjects(UUID environmentId, UUID projectId, UUID workspaceId) {
        Set<UUID> candidates = new LinkedHashSet<>();
        var wsMembers = workspaceMemberRepository.findByWorkspaceId(workspaceId);
        wsMembers.forEach(m -> candidates.add(m.getUserId()));
        var projOverrides = projectOverrideRepository.findByProjectId(projectId);
        projOverrides.forEach(o -> candidates.add(o.getUserId()));
        var envOverrides = envOverrideRepository.findByEnvironmentId(environmentId);
        envOverrides.forEach(o -> candidates.add(o.getUserId()));

        return candidates.stream()
                .filter(userId -> effectiveRole(
                                userId, environmentId, projectId, workspaceId, wsMembers, projOverrides, envOverrides)
                        .filter(r -> r.ordinal() <= EDITOR_ORDINAL)
                        .isPresent())
                .map(userRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(UserEntity::getOidcSubjectId)
                .filter(s -> s != null && !s.isBlank())
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Max-permission wins (lowest ordinal), matching {@code PermissionService}: env override
     * beats project override beats workspace membership — but only if strictly more permissive.
     */
    private java.util.Optional<MemberRole> effectiveRole(
            UUID userId,
            UUID environmentId,
            UUID projectId,
            UUID workspaceId,
            List<eu.appbahn.platform.workspace.entity.WorkspaceMemberEntity> wsMembers,
            List<eu.appbahn.platform.workspace.entity.ProjectMemberOverrideEntity> projOverrides,
            List<eu.appbahn.platform.workspace.entity.EnvironmentMemberOverrideEntity> envOverrides) {
        MemberRole role = wsMembers.stream()
                .filter(m -> userId.equals(m.getUserId()))
                .findFirst()
                .map(m -> MemberRole.valueOf(m.getRole()))
                .orElse(null);
        MemberRole projOverride = projOverrides.stream()
                .filter(o -> userId.equals(o.getUserId()))
                .findFirst()
                .map(o -> MemberRole.valueOf(o.getRole()))
                .orElse(null);
        if (projOverride != null && (role == null || projOverride.ordinal() < role.ordinal())) {
            role = projOverride;
        }
        MemberRole envOverride = envOverrides.stream()
                .filter(o -> userId.equals(o.getUserId()))
                .findFirst()
                .map(o -> MemberRole.valueOf(o.getRole()))
                .orElse(null);
        if (envOverride != null && (role == null || envOverride.ordinal() < role.ordinal())) {
            role = envOverride;
        }
        return java.util.Optional.ofNullable(role);
    }

    private List<String> resolveAllowedGroups(UUID workspaceId) {
        return oidcGroupMappingRepository.findByWorkspaceId(workspaceId).stream()
                .filter(m -> MemberRole.valueOf(m.getRole()).ordinal() <= EDITOR_ORDINAL)
                .map(eu.appbahn.platform.workspace.entity.OidcGroupMappingEntity::getOidcGroup)
                .sorted()
                .distinct()
                .collect(Collectors.toList());
    }

    private int parseMaxResources(String quotaJson) {
        if (quotaJson == null || quotaJson.isBlank()) {
            return 0;
        }
        try {
            Quota quota = JsonUtil.parseJson(objectMapper, quotaJson, Quota.class);
            Integer max = quota.getMaxResources();
            return (max == null || max < 0) ? 0 : max;
        } catch (RuntimeException e) {
            // Malformed quota JSON is a platform-side data error — fail open on the per-env
            // cap rather than locking out all kubectl applies. Platform-side REST path still
            // rejects via QuotaService.
            return 0;
        }
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
    }
}

package eu.appbahn.platform.tunnel.push;

import eu.appbahn.platform.api.Quota;
import eu.appbahn.platform.api.tunnel.EnvironmentEntry;
import eu.appbahn.platform.api.tunnel.ProjectEntry;
import eu.appbahn.platform.api.tunnel.QuotaDimensions;
import eu.appbahn.platform.api.tunnel.QuotaRbacCachePush;
import eu.appbahn.platform.api.tunnel.QuotaRbacSnapshot;
import eu.appbahn.platform.api.tunnel.WorkspaceEntry;
import eu.appbahn.platform.resource.entity.ResourceCacheEntity;
import eu.appbahn.platform.resource.repository.ResourceCacheRepository;
import eu.appbahn.platform.user.entity.UserEntity;
import eu.appbahn.platform.user.repository.UserRepository;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.entity.ProjectEntity;
import eu.appbahn.platform.workspace.entity.WorkspaceEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentMemberOverrideRepository;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.platform.workspace.repository.OidcGroupMappingRepository;
import eu.appbahn.platform.workspace.repository.ProjectMemberOverrideRepository;
import eu.appbahn.platform.workspace.repository.ProjectRepository;
import eu.appbahn.platform.workspace.repository.WorkspaceMemberRepository;
import eu.appbahn.platform.workspace.repository.WorkspaceRepository;
import eu.appbahn.platform.workspace.service.NamespaceService;
import eu.appbahn.shared.model.MemberRole;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produces a {@link QuotaRbacCachePush} carrying everything the operator's admission webhook
 * needs to decide allow/deny locally: env↔namespace mappings, per-dimension quota caps at
 * env/project/workspace levels, per-env current usage (so the webhook can roll up to
 * project/workspace scopes by summing), and the RBAC closure (OIDC subjects + groups that
 * resolve to {@link MemberRole#EDITOR} or higher on the environment).
 *
 * <p>Revision is content-addressed: identical snapshot bytes on any replica produce the same
 * revision, so the operator dedupes repeated pushes without cross-replica coordination.
 */
@Service
public class QuotaRbacSnapshotBuilder {

    private static final int EDITOR_ORDINAL = MemberRole.EDITOR.ordinal();

    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final OidcGroupMappingRepository oidcGroupMappingRepository;
    private final ProjectMemberOverrideRepository projectOverrideRepository;
    private final EnvironmentMemberOverrideRepository envOverrideRepository;
    private final ResourceCacheRepository resourceCacheRepository;
    private final UserRepository userRepository;
    private final NamespaceService namespaceService;
    private final SnapshotRevisions revisions;
    private final List<String> platformAdminGroups;

    public QuotaRbacSnapshotBuilder(
            EnvironmentRepository environmentRepository,
            ProjectRepository projectRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            OidcGroupMappingRepository oidcGroupMappingRepository,
            ProjectMemberOverrideRepository projectOverrideRepository,
            EnvironmentMemberOverrideRepository envOverrideRepository,
            ResourceCacheRepository resourceCacheRepository,
            UserRepository userRepository,
            NamespaceService namespaceService,
            SnapshotRevisions revisions,
            @Value("${platform.admin-groups:}") String platformAdminGroups) {
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.oidcGroupMappingRepository = oidcGroupMappingRepository;
        this.projectOverrideRepository = projectOverrideRepository;
        this.envOverrideRepository = envOverrideRepository;
        this.resourceCacheRepository = resourceCacheRepository;
        this.userRepository = userRepository;
        this.namespaceService = namespaceService;
        this.revisions = revisions;
        this.platformAdminGroups = splitCsv(platformAdminGroups);
    }

    @Transactional(readOnly = true)
    public QuotaRbacCachePush buildFor(String clusterName) {
        var snapshot = new QuotaRbacSnapshot();
        snapshot.setPlatformAdminGroups(new ArrayList<>(platformAdminGroups));

        List<EnvironmentEntity> envs = environmentRepository.findByTargetCluster(clusterName).stream()
                .sorted(Comparator.comparing(EnvironmentEntity::getSlug))
                .toList();

        Map<UUID, ProjectEntity> projectsById = loadProjects(envs);
        Map<UUID, WorkspaceEntity> workspacesById = loadWorkspaces(projectsById.values());
        Map<UUID, EnvUsage> usageByEnvId = loadEnvUsage(envs);

        for (EnvironmentEntity env : envs) {
            snapshot.getEnvironments().add(buildEnvEntry(env, projectsById, workspacesById, usageByEnvId));
        }
        projectsById.values().stream()
                .sorted(Comparator.comparing(ProjectEntity::getSlug))
                .forEach(p -> snapshot.getProjects().add(buildProjectEntry(p, workspacesById)));
        workspacesById.values().stream()
                .sorted(Comparator.comparing(WorkspaceEntity::getSlug))
                .forEach(w -> snapshot.getWorkspaces().add(buildWorkspaceEntry(w)));

        var push = new QuotaRbacCachePush();
        push.setSnapshot(snapshot);
        push.setRevision(revisions.contentRevision(snapshot));
        return push;
    }

    private Map<UUID, ProjectEntity> loadProjects(List<EnvironmentEntity> envs) {
        Set<UUID> projectIds =
                envs.stream().map(EnvironmentEntity::getProjectId).collect(Collectors.toSet());
        Map<UUID, ProjectEntity> result = new LinkedHashMap<>();
        projectRepository.findAllById(projectIds).forEach(p -> result.put(p.getId(), p));
        return result;
    }

    private Map<UUID, WorkspaceEntity> loadWorkspaces(java.util.Collection<ProjectEntity> projects) {
        Set<UUID> workspaceIds =
                projects.stream().map(ProjectEntity::getWorkspaceId).collect(Collectors.toSet());
        Map<UUID, WorkspaceEntity> result = new LinkedHashMap<>();
        workspaceRepository.findAllById(workspaceIds).forEach(w -> result.put(w.getId(), w));
        return result;
    }

    private Map<UUID, EnvUsage> loadEnvUsage(List<EnvironmentEntity> envs) {
        Map<UUID, EnvUsage> usage = new HashMap<>();
        envs.forEach(env -> usage.put(env.getId(), EnvUsage.empty()));
        if (envs.isEmpty()) {
            return usage;
        }
        List<UUID> envIds = envs.stream().map(EnvironmentEntity::getId).toList();
        List<ResourceCacheEntity> resources = resourceCacheRepository.findByEnvironmentIdIn(envIds);
        for (ResourceCacheEntity r : resources) {
            usage.compute(
                    r.getEnvironmentId(),
                    (id, acc) -> EnvUsage.fromConfig(acc == null ? EnvUsage.empty() : acc, r.getConfig()));
        }
        return usage;
    }

    private EnvironmentEntry buildEnvEntry(
            EnvironmentEntity env,
            Map<UUID, ProjectEntity> projectsById,
            Map<UUID, WorkspaceEntity> workspacesById,
            Map<UUID, EnvUsage> usageByEnvId) {
        EnvUsage usage = usageByEnvId.getOrDefault(env.getId(), EnvUsage.empty());

        var entry = new EnvironmentEntry();
        entry.setSlug(env.getSlug());
        entry.setNamespace(namespaceService.computeNamespace(env.getSlug()));
        entry.setLimits(dimensionsFromQuota(env.getQuota()));
        entry.setCurrent(dimensionsFromUsage(usage));

        ProjectEntity project = projectsById.get(env.getProjectId());
        if (project == null) {
            // Orphan env — no workspace to resolve RBAC from. Fail-closed on users/groups,
            // parent slugs stay empty so the webhook treats them as unknown scopes.
            return entry;
        }
        entry.setProjectSlug(project.getSlug());
        WorkspaceEntity workspace = workspacesById.get(project.getWorkspaceId());
        if (workspace != null) {
            entry.setWorkspaceSlug(workspace.getSlug());
        }
        UUID workspaceId = project.getWorkspaceId();

        entry.setAllowedUserSubjects(
                new ArrayList<>(resolveAllowedSubjects(env.getId(), project.getId(), workspaceId)));
        entry.setAllowedOidcGroups(new ArrayList<>(resolveAllowedGroups(workspaceId)));
        return entry;
    }

    private ProjectEntry buildProjectEntry(ProjectEntity project, Map<UUID, WorkspaceEntity> workspacesById) {
        var entry = new ProjectEntry();
        entry.setSlug(project.getSlug());
        entry.setLimits(dimensionsFromQuota(project.getQuota()));
        WorkspaceEntity workspace = workspacesById.get(project.getWorkspaceId());
        if (workspace != null) {
            entry.setWorkspaceSlug(workspace.getSlug());
        }
        return entry;
    }

    private WorkspaceEntry buildWorkspaceEntry(WorkspaceEntity workspace) {
        var entry = new WorkspaceEntry();
        entry.setSlug(workspace.getSlug());
        entry.setLimits(dimensionsFromQuota(workspace.getQuota()));
        return entry;
    }

    private static QuotaDimensions dimensionsFromQuota(Quota quota) {
        var d = new QuotaDimensions();
        if (quota == null) {
            return d;
        }
        // No maxReplicas on the Quota POJO → always 0 (fail-open on the webhook).
        d.setResources(asUint(quota.getMaxResources()));
        d.setCpuMillicores(coresToMillicores(quota.getMaxCpuCores()));
        d.setMemoryMb(asUint(quota.getMaxMemoryMb()));
        d.setStorageGb(asUint(quota.getMaxStorageGb()));
        return d;
    }

    private static QuotaDimensions dimensionsFromUsage(EnvUsage usage) {
        var d = new QuotaDimensions();
        d.setResources(usage.resources());
        d.setCpuMillicores(usage.cpuMillicores());
        d.setMemoryMb(usage.memoryMb());
        d.setStorageGb(usage.storageGb());
        d.setReplicas(usage.replicas());
        return d;
    }

    /**
     * Builds the set of OIDC subjects resolving to {@code EDITOR+} on {@code environmentId}.
     * Mirrors {@link eu.appbahn.platform.workspace.service.PermissionService#resolveEnvironmentRole}
     * inverted: iterate every user touched by a workspace membership + project/env override,
     * then test each against the same precedence rules. Sorted so the resulting JSON bytes
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

    private static int asUint(Integer value) {
        if (value == null || value < 0) {
            return 0;
        }
        return value;
    }

    private static int coresToMillicores(Double cores) {
        if (cores == null || cores < 0) {
            return 0;
        }
        return (int) Math.round(cores * 1000.0);
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

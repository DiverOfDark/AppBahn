package eu.appbahn.platform.resource.stats;

import eu.appbahn.platform.api.stats.EnvironmentRollup;
import eu.appbahn.platform.api.stats.EnvironmentStats;
import eu.appbahn.platform.api.stats.ProjectStats;
import eu.appbahn.platform.api.stats.WorkspaceStats;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.workspace.entity.WorkspaceEntity;
import eu.appbahn.platform.workspace.repository.ProjectRepository;
import eu.appbahn.platform.workspace.repository.WorkspaceRepository;
import eu.appbahn.platform.workspace.service.PermissionService;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.model.MemberRole;
import io.fabric8.kubernetes.api.model.Quantity;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Backs the {@code /workspaces/stats}, {@code /projects/stats} and {@code /environments/stats}
 * endpoints. Every counter comes from a single aggregate SQL query in {@link StatsRepository};
 * this service only translates typed rollup rows into API DTOs and enforces per-slug permissions.
 *
 * <p>Permission semantics: unknown / non-viewable workspace slugs are silently dropped from
 * the response — the contract is "give me what I can see", not "fail the whole request when
 * one slug is invalid".
 */
@Service
public class StatsService {

    private static final Duration DEPLOY_WINDOW = Duration.ofDays(7);

    private final StatsRepository statsRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final PermissionService permissionService;

    public StatsService(
            StatsRepository statsRepository,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            PermissionService permissionService) {
        this.statsRepository = statsRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.permissionService = permissionService;
    }

    public List<WorkspaceStats> workspaceStats(List<String> slugs, AuthContext ctx) {
        if (slugs.isEmpty()) {
            return List.of();
        }
        Map<String, WorkspaceEntity> bySlug = workspaceRepository.findAllBySlugIn(slugs).stream()
                .collect(Collectors.toMap(WorkspaceEntity::getSlug, w -> w));

        List<UUID> readableIds = slugs.stream()
                .map(bySlug::get)
                .filter(w -> w != null && permissionService.resolveWorkspaceRole(ctx, w.getId()) != null)
                .map(WorkspaceEntity::getId)
                .toList();
        if (readableIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, WorkspaceStats> byId = new HashMap<>();
        for (WorkspaceRollupRow row : statsRepository.workspaceRollups(readableIds)) {
            var s = new WorkspaceStats();
            s.setSlug(row.slug());
            s.setProjectCount(row.projectCount());
            s.setResourceCount(row.resourceCount());
            s.setClusterCount(row.clusterCount());
            s.setMemberCount(row.memberCount());
            s.setLastEventAt(toOffsetDateTime(row.lastEventAt()));
            byId.put(row.workspaceId(), s);
        }

        // Preserve the caller's slug order — keeps the SPA's render order stable.
        List<WorkspaceStats> result = new ArrayList<>(readableIds.size());
        for (String slug : slugs) {
            var ws = bySlug.get(slug);
            if (ws == null) continue;
            var stats = byId.get(ws.getId());
            if (stats != null) {
                result.add(stats);
            }
        }
        return result;
    }

    public List<ProjectStats> projectStats(String workspaceSlug, AuthContext ctx) {
        WorkspaceEntity workspace = workspaceRepository
                .findBySlug(workspaceSlug)
                .orElseThrow(() -> new NotFoundException("Workspace not found: " + workspaceSlug));
        permissionService.requireWorkspaceRole(ctx, workspace.getId(), MemberRole.VIEWER);

        Instant since = Instant.now().minus(DEPLOY_WINDOW);
        List<ProjectRollupRow> projectRows = statsRepository.projectRollups(workspace.getId(), since);
        List<EnvironmentStatusRollupRow> envRows =
                statsRepository.environmentStatusRollupsForWorkspace(workspace.getId());

        Map<UUID, List<EnvironmentRollup>> envsByProject = new HashMap<>();
        for (EnvironmentStatusRollupRow row : envRows) {
            var rollup = new EnvironmentRollup();
            rollup.setSlug(row.slug());
            rollup.setStatus(toPhase(row.status()));
            envsByProject
                    .computeIfAbsent(row.projectId(), k -> new ArrayList<>())
                    .add(rollup);
        }

        List<ProjectStats> result = new ArrayList<>(projectRows.size());
        for (ProjectRollupRow row : projectRows) {
            var stats = new ProjectStats();
            stats.setSlug(row.slug());
            stats.setServices(row.services());
            stats.setDeploys7d(row.deploys7d());
            long ready = row.readyCount();
            long total = row.totalCount();
            stats.setUptimePct(total == 0 ? 100.0 : (ready * 100.0) / total);
            stats.setLastDeployAt(toOffsetDateTime(row.lastDeployAt()));
            stats.setEnvs(envsByProject.getOrDefault(row.projectId(), List.of()));
            result.add(stats);
        }
        return result;
    }

    public List<EnvironmentStats> environmentStats(String projectSlug, AuthContext ctx) {
        var project = projectRepository
                .findBySlug(projectSlug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectSlug));
        permissionService.requireProjectRole(ctx, project.getId(), MemberRole.VIEWER);

        List<EnvironmentStatsRow> rows = statsRepository.environmentStatsForProject(project.getId());
        List<EnvironmentStats> result = new ArrayList<>(rows.size());
        for (EnvironmentStatsRow row : rows) {
            var stats = new EnvironmentStats();
            stats.setSlug(row.slug());
            stats.setAggregateStatus(toPhase(row.status()));
            stats.setConfiguredCpu(sumCpu(row.cpuQuantities(), row.replicas()));
            stats.setConfiguredMemory(sumMemoryMi(row.memoryQuantities(), row.replicas()));
            result.add(stats);
        }
        return result;
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static ResourcePhase toPhase(String name) {
        if (name == null) return null;
        return ResourcePhase.valueOf(name);
    }

    /**
     * Aggregates per-resource CPU quantities into a single millicore {@link Quantity}.
     * Falls back to {@code null} when no resource declared CPU.
     */
    private static Quantity sumCpu(String[] quantities, Integer[] replicas) {
        BigDecimal totalMillicores = BigDecimal.ZERO;
        boolean any = false;
        for (int i = 0; i < quantities.length; i++) {
            String q = quantities[i];
            if (q == null || q.isBlank()) continue;
            int reps = replicas != null && i < replicas.length && replicas[i] != null ? replicas[i] : 1;
            BigDecimal cores = new Quantity(q).getNumericalAmount();
            // Render as millicores ("m") so 0.25 cpu × 3 replicas = 750m, not 0.75.
            BigDecimal millicores = cores.multiply(BigDecimal.valueOf(1000L)).multiply(BigDecimal.valueOf(reps));
            totalMillicores = totalMillicores.add(millicores);
            any = true;
        }
        return any ? new Quantity(totalMillicores.toPlainString() + "m") : null;
    }

    /**
     * Aggregates per-resource memory quantities into a single Mi {@link Quantity}.
     */
    private static Quantity sumMemoryMi(String[] quantities, Integer[] replicas) {
        long totalBytes = 0L;
        boolean any = false;
        for (int i = 0; i < quantities.length; i++) {
            String q = quantities[i];
            if (q == null || q.isBlank()) continue;
            int reps = replicas != null && i < replicas.length && replicas[i] != null ? replicas[i] : 1;
            long bytes = Quantity.getAmountInBytes(new Quantity(q)).longValueExact();
            totalBytes = Math.addExact(totalBytes, Math.multiplyExact(bytes, (long) reps));
            any = true;
        }
        if (!any) return null;
        long mi = totalBytes / (1024L * 1024L);
        return new Quantity(mi + "Mi");
    }
}

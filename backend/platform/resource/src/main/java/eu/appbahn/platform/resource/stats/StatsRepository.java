package eu.appbahn.platform.resource.stats;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Server-side rollups for the bulk-stats endpoints. Every method here is a single SQL
 * aggregate — the API contract is to never load entities and count them in Java. Each
 * native query is mapped into a typed record ({@link WorkspaceRollupRow},
 * {@link ProjectRollupRow}, {@link EnvironmentStatusRollupRow}, {@link EnvironmentStatsRow})
 * so callers never see {@code Object[]}.
 */
@Repository
public class StatsRepository {

    /** Worst-status precedence: a numerically lower rank loses to the higher. */
    static final String STATUS_RANK_CASE = """
            CASE rc.status
                WHEN 'ERROR'      THEN 6
                WHEN 'DEGRADED'   THEN 5
                WHEN 'RESTARTING' THEN 4
                WHEN 'PENDING'    THEN 3
                WHEN 'STOPPED'    THEN 2
                WHEN 'READY'      THEN 1
                ELSE 0
            END
            """;

    private static final String RANK_TO_STATUS_CASE = """
            CASE MAX(%s)
                WHEN 6 THEN 'ERROR'
                WHEN 5 THEN 'DEGRADED'
                WHEN 4 THEN 'RESTARTING'
                WHEN 3 THEN 'PENDING'
                WHEN 2 THEN 'STOPPED'
                WHEN 1 THEN 'READY'
                ELSE NULL
            END
            """.formatted(STATUS_RANK_CASE);

    private final EntityManager em;

    public StatsRepository(EntityManager em) {
        this.em = em;
    }

    /**
     * Per-workspace counters, one row per requested workspace id. Workspaces with no
     * matches in any side table still produce a row (zeroes / null {@code lastEventAt})
     * because the outer driver is {@code workspace} itself.
     */
    @SuppressWarnings("unchecked")
    public List<WorkspaceRollupRow> workspaceRollups(Collection<UUID> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows =
                em.createNativeQuery("""
                        SELECT w.id,
                               w.slug,
                               COALESCE((SELECT COUNT(*) FROM project p
                                          WHERE p.workspace_id = w.id), 0)                 AS project_count,
                               COALESCE((SELECT COUNT(*) FROM resource_cache rc
                                          JOIN environment e ON rc.environment_id = e.id
                                          JOIN project p     ON e.project_id     = p.id
                                          WHERE p.workspace_id = w.id), 0)                  AS resource_count,
                               COALESCE((SELECT COUNT(DISTINCT e.target_cluster) FROM environment e
                                          JOIN project p ON e.project_id = p.id
                                          WHERE p.workspace_id = w.id), 0)                  AS cluster_count,
                               COALESCE((SELECT COUNT(*) FROM workspace_member m
                                          WHERE m.workspace_id = w.id), 0)                  AS member_count,
                               (SELECT MAX(a.timestamp) FROM audit_log a
                                  WHERE a.workspace_id = w.id)                              AS last_event_at
                        FROM workspace w
                        WHERE w.id IN (:ids)
                        """).setParameter("ids", workspaceIds).getResultList();
        return rows.stream()
                .map(r -> new WorkspaceRollupRow(
                        (UUID) r[0], (String) r[1], asLong(r[2]), asLong(r[3]), asLong(r[4]), asLong(r[5]), (Instant)
                                r[6]))
                .toList();
    }

    /**
     * Per-project counters, one row per project in the workspace, ordered by slug.
     */
    @SuppressWarnings("unchecked")
    public List<ProjectRollupRow> projectRollups(UUID workspaceId, Instant deployWindowStart) {
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT p.id,
                               p.slug,
                               COALESCE((SELECT COUNT(*) FROM resource_cache rc
                                          JOIN environment e ON rc.environment_id = e.id
                                          WHERE e.project_id = p.id), 0)                    AS services,
                               COALESCE((SELECT COUNT(*) FROM deployment d
                                          JOIN environment e ON d.environment_id = e.id
                                          WHERE e.project_id = p.id
                                            AND d.created_at >= :since), 0)                 AS deploys7d,
                               COALESCE((SELECT COUNT(*) FROM resource_cache rc
                                          JOIN environment e ON rc.environment_id = e.id
                                          WHERE e.project_id = p.id
                                            AND rc.status = 'READY'), 0)                    AS ready_count,
                               COALESCE((SELECT COUNT(*) FROM resource_cache rc
                                          JOIN environment e ON rc.environment_id = e.id
                                          WHERE e.project_id = p.id), 0)                    AS total_count,
                               (SELECT MAX(d.created_at) FROM deployment d
                                  JOIN environment e ON d.environment_id = e.id
                                  WHERE e.project_id = p.id)                                AS last_deploy_at
                        FROM project p
                        WHERE p.workspace_id = :wsId
                        ORDER BY p.slug
                        """)
                .setParameter("wsId", workspaceId)
                .setParameter("since", deployWindowStart)
                .getResultList();
        return rows.stream()
                .map(r -> new ProjectRollupRow(
                        (UUID) r[0], (String) r[1], asLong(r[2]), asLong(r[3]), asLong(r[4]), asLong(r[5]), (Instant)
                                r[6]))
                .toList();
    }

    /**
     * Per-environment status rollups for every env in the workspace, used to populate
     * {@code ProjectStats.envs[]}. Status is the worst-rank status across the env's
     * resources (see {@link #STATUS_RANK_CASE}); environments with zero resources get
     * {@code null}.
     */
    @SuppressWarnings("unchecked")
    public List<EnvironmentStatusRollupRow> environmentStatusRollupsForWorkspace(UUID workspaceId) {
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT e.project_id,
                               e.slug,
                               %s AS status
                        FROM environment e
                        JOIN project p ON e.project_id = p.id
                        LEFT JOIN resource_cache rc ON rc.environment_id = e.id
                        WHERE p.workspace_id = :wsId
                        GROUP BY e.project_id, e.slug
                        ORDER BY e.slug
                        """.formatted(RANK_TO_STATUS_CASE))
                .setParameter("wsId", workspaceId)
                .getResultList();
        return rows.stream()
                .map(r -> new EnvironmentStatusRollupRow((UUID) r[0], (String) r[1], (String) r[2]))
                .toList();
    }

    /**
     * Per-environment stats for every env in a project: worst-status + sums of configured
     * CPU/memory across all resources. CPU/memory values come from the {@code config}
     * JSONB on each resource and arrive as the original {@link io.fabric8.kubernetes.api.model.Quantity}
     * strings — {@link StatsService} converts them via Quantity arithmetic, not SQL math.
     */
    @SuppressWarnings("unchecked")
    public List<EnvironmentStatsRow> environmentStatsForProject(UUID projectId) {
        // FILTER on every aggregate so cpu_qs[i], mem_qs[i] and replicas[i] always
        // refer to the same resource — the caller relies on positional correlation.
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT e.id,
                               e.slug,
                               %s AS status,
                               ARRAY_AGG(rc.config -> 'hosting' ->> 'cpu')
                                   FILTER (WHERE rc.slug IS NOT NULL)               AS cpu_qs,
                               ARRAY_AGG(rc.config -> 'hosting' ->> 'memory')
                                   FILTER (WHERE rc.slug IS NOT NULL)               AS mem_qs,
                               ARRAY_AGG(
                                   COALESCE(
                                       CAST(rc.config -> 'hosting' ->> 'maxReplicas' AS INTEGER),
                                       CAST(rc.config -> 'hosting' ->> 'minReplicas' AS INTEGER),
                                       1
                                   )
                               ) FILTER (WHERE rc.slug IS NOT NULL)                 AS replicas
                        FROM environment e
                        LEFT JOIN resource_cache rc ON rc.environment_id = e.id
                        WHERE e.project_id = :projId
                        GROUP BY e.id, e.slug
                        ORDER BY e.slug
                        """.formatted(RANK_TO_STATUS_CASE))
                .setParameter("projId", projectId)
                .getResultList();
        return rows.stream()
                .map(r -> new EnvironmentStatsRow(
                        (UUID) r[0],
                        (String) r[1],
                        (String) r[2],
                        toStringArray(r[3]),
                        toStringArray(r[4]),
                        toIntegerArray(r[5])))
                .toList();
    }

    private static long asLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        throw new IllegalStateException("Expected numeric, got: " + value.getClass());
    }

    private static String[] toStringArray(Object value) {
        if (value == null) return new String[0];
        if (value instanceof String[] arr) return arr;
        if (value instanceof Object[] arr) {
            String[] out = new String[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = arr[i] == null ? null : arr[i].toString();
            }
            return out;
        }
        throw new IllegalStateException("Expected String[], got: " + value.getClass());
    }

    private static Integer[] toIntegerArray(Object value) {
        if (value == null) return new Integer[0];
        if (value instanceof Integer[] arr) return arr;
        if (value instanceof Object[] arr) {
            Integer[] out = new Integer[arr.length];
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] == null) {
                    out[i] = null;
                } else if (arr[i] instanceof Number n) {
                    out[i] = n.intValue();
                } else {
                    out[i] = Integer.parseInt(arr[i].toString());
                }
            }
            return out;
        }
        throw new IllegalStateException("Expected Integer[], got: " + value.getClass());
    }
}

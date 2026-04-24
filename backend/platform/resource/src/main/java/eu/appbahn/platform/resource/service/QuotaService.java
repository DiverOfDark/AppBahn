package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.common.exception.QuotaExceededException;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceConfig;
import io.fabric8.kubernetes.api.model.Quantity;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    private final EntityManager entityManager;

    private final int defaultMaxResources;
    private final double defaultMaxCpuCores;
    private final int defaultMaxMemoryMb;

    /**
     * Fetches all resources across the entire workspace that the target environment belongs to,
     * plus the hierarchy quotas. Each row carries its environment_id and project_id so we can
     * bucket resources into env / project / workspace levels in Java.
     *
     * Returns rows: [cpu_str, memory_str, replicas, max_replicas, resource_slug,
     *                resource_env_id, resource_proj_id,
     *                env_max_resources, env_max_cpu_cores, env_max_memory_mb,
     *                target_proj_id,
     *                proj_max_resources, proj_max_cpu_cores, proj_max_memory_mb,
     *                ws_max_resources, ws_max_cpu_cores, ws_max_memory_mb]
     *
     * LEFT JOIN ensures we get at least one row (with nulls for resource columns) even when
     * the workspace has no resources at all.
     */
    private static final String USAGE_SQL = """
            SELECT rc.config -> 'hosting' ->> 'cpu'                         AS cpu_str,
                   rc.config -> 'hosting' ->> 'memory'                      AS memory_str,
                   CAST(rc.config -> 'hosting' ->> 'replicas' AS integer)   AS replicas,
                   CAST(rc.config -> 'hosting' ->> 'maxReplicas' AS integer) AS max_replicas,
                   rc.slug                                                  AS resource_slug,
                   e_res.id                                                 AS resource_env_id,
                   p_res.id                                                 AS resource_proj_id,
                   (e_target.quota ->> 'maxResources')::INTEGER             AS env_max_resources,
                   (e_target.quota ->> 'maxCpuCores')::DOUBLE PRECISION     AS env_max_cpu_cores,
                   (e_target.quota ->> 'maxMemoryMb')::INTEGER              AS env_max_memory_mb,
                   p_target.id                                              AS target_proj_id,
                   (p_target.quota ->> 'maxResources')::INTEGER             AS proj_max_resources,
                   (p_target.quota ->> 'maxCpuCores')::DOUBLE PRECISION     AS proj_max_cpu_cores,
                   (p_target.quota ->> 'maxMemoryMb')::INTEGER              AS proj_max_memory_mb,
                   (w.quota ->> 'maxResources')::INTEGER                    AS ws_max_resources,
                   (w.quota ->> 'maxCpuCores')::DOUBLE PRECISION            AS ws_max_cpu_cores,
                   (w.quota ->> 'maxMemoryMb')::INTEGER                     AS ws_max_memory_mb
            FROM environment e_target
            JOIN project p_target ON e_target.project_id = p_target.id
            JOIN workspace w       ON p_target.workspace_id = w.id
            LEFT JOIN project p_res        ON p_res.workspace_id = w.id
            LEFT JOIN environment e_res    ON e_res.project_id = p_res.id
            LEFT JOIN resource_cache rc    ON rc.environment_id = e_res.id
            WHERE e_target.id = :envId
            """;

    public QuotaService(
            EntityManager entityManager,
            @Value("${platform.quota.default-max-resources:50}") int defaultMaxResources,
            @Value("${platform.quota.default-max-cpu-cores:16.0}") double defaultMaxCpuCores,
            @Value("${platform.quota.default-max-memory-mb:32768}") int defaultMaxMemoryMb) {
        this.entityManager = entityManager;
        this.defaultMaxResources = defaultMaxResources;
        this.defaultMaxCpuCores = defaultMaxCpuCores;
        this.defaultMaxMemoryMb = defaultMaxMemoryMb;
    }

    /**
     * Check quotas for creating or updating a resource in the given environment.
     * Must be called within a @Transactional context.
     *
     * @param environmentId the environment UUID
     * @param excludeSlug slug to exclude from current usage (for updates), null for creates
     * @param newResourceConfig config of the new/updated resource, may be null
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkQuota(UUID environmentId, String excludeSlug, ResourceConfig newResourceConfig) {
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(CAST(:envId AS TEXT)))")
                .setParameter("envId", environmentId.toString())
                .getSingleResult();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager
                .createNativeQuery(USAGE_SQL)
                .setParameter("envId", environmentId)
                .getResultList();

        if (rows.isEmpty()) {
            // Environment isn't in the hierarchy — fall back to platform defaults.
            var newUsage = newResourceUsage(newResourceConfig);
            checkPlatformDefaults(1, newUsage.cpu, newUsage.memory);
            return;
        }

        Object[] first = rows.get(0);
        var envLimits = limitsAt(first, 7);
        UUID targetProjId = (UUID) first[10];
        var projLimits = limitsAt(first, 11);
        var wsLimits = limitsAt(first, 14);

        var envUsage = new Usage(0, 0, 0);
        var projUsage = new Usage(0, 0, 0);
        var wsUsage = new Usage(0, 0, 0);

        for (Object[] row : rows) {
            String resourceSlug = (String) row[4];
            if (resourceSlug == null) {
                continue; // LEFT JOIN produced null — no resource
            }
            if (excludeSlug != null && excludeSlug.equals(resourceSlug)) {
                continue;
            }

            String cpuStr = (String) row[0];
            String memoryStr = (String) row[1];
            Integer replicas = (Integer) row[2];
            Integer maxReplicas = (Integer) row[3];
            int reps = maxReplicas != null ? maxReplicas : (replicas != null ? replicas : Labels.DEFAULT_REPLICAS);
            double cpu = cpuCores(cpuStr) * reps;
            int memory = memoryMb(memoryStr) * reps;

            UUID resourceEnvId = (UUID) row[5];
            UUID resourceProjId = (UUID) row[6];

            wsUsage = wsUsage.add(1, cpu, memory);
            if (targetProjId.equals(resourceProjId)) {
                projUsage = projUsage.add(1, cpu, memory);
            }
            if (environmentId.equals(resourceEnvId)) {
                envUsage = envUsage.add(1, cpu, memory);
            }
        }

        var newUsage = newResourceUsage(newResourceConfig);
        envUsage = envUsage.add(1, newUsage.cpu, newUsage.memory);
        projUsage = projUsage.add(1, newUsage.cpu, newUsage.memory);
        wsUsage = wsUsage.add(1, newUsage.cpu, newUsage.memory);

        checkQuotaLevel(envLimits, envUsage, "environment");
        checkQuotaLevel(projLimits, projUsage, "project");
        checkQuotaLevel(wsLimits, wsUsage, "workspace");
        checkPlatformDefaults(envUsage.count, envUsage.cpu, envUsage.memory);
    }

    private static QuotaLimits limitsAt(Object[] row, int offset) {
        return new QuotaLimits((Integer) row[offset], toDouble(row[offset + 1]), (Integer) row[offset + 2]);
    }

    private static Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Double d) return d;
        if (value instanceof Number n) return n.doubleValue();
        throw new IllegalStateException("Unexpected numeric type: " + value.getClass());
    }

    private void checkQuotaLevel(QuotaLimits limits, Usage usage, String level) {
        if (limits.maxResources != null && usage.count > limits.maxResources) {
            throw new QuotaExceededException("maxResources", limits.maxResources, level);
        }
        if (limits.maxCpuCores != null && usage.cpu > limits.maxCpuCores) {
            throw new QuotaExceededException("maxCpuCores", limits.maxCpuCores, level);
        }
        if (limits.maxMemoryMb != null && usage.memory > limits.maxMemoryMb) {
            throw new QuotaExceededException("maxMemoryMb", limits.maxMemoryMb, level);
        }
    }

    private void checkPlatformDefaults(int count, double cpu, int memory) {
        if (count > defaultMaxResources) {
            throw new QuotaExceededException("maxResources", defaultMaxResources, "platform");
        }
        if (cpu > defaultMaxCpuCores) {
            throw new QuotaExceededException("maxCpuCores", defaultMaxCpuCores, "platform");
        }
        if (memory > defaultMaxMemoryMb) {
            throw new QuotaExceededException("maxMemoryMb", defaultMaxMemoryMb, "platform");
        }
    }

    private record QuotaLimits(Integer maxResources, Double maxCpuCores, Integer maxMemoryMb) {}

    private record Usage(int count, double cpu, int memory) {
        Usage add(int count, double cpu, int memory) {
            return new Usage(this.count + count, this.cpu + cpu, this.memory + memory);
        }
    }

    private static Usage newResourceUsage(ResourceConfig config) {
        if (config == null || config.getHosting() == null) {
            return new Usage(0, 0, 0);
        }
        var hosting = config.getHosting();
        int reps = hosting.getEffectiveReplicasForQuota() != null
                ? hosting.getEffectiveReplicasForQuota()
                : Labels.DEFAULT_REPLICAS;
        return new Usage(0, cpuCores(hosting.getCpu()) * reps, memoryMb(hosting.getMemory()) * reps);
    }

    static double cpuCores(String cpuStr) {
        if (cpuStr == null || cpuStr.isBlank()) return 0;
        return new Quantity(cpuStr).getNumericalAmount().doubleValue();
    }

    static int memoryMb(String memStr) {
        if (memStr == null || memStr.isBlank()) return 0;
        return (int) (Quantity.getAmountInBytes(new Quantity(memStr)).longValue() / (1024 * 1024));
    }

    private static double cpuCores(Quantity q) {
        if (q == null) return 0;
        return q.getNumericalAmount().doubleValue();
    }

    private static int memoryMb(Quantity q) {
        if (q == null) return 0;
        return (int) (Quantity.getAmountInBytes(q).longValue() / (1024 * 1024));
    }
}

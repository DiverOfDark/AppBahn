package eu.appbahn.platform.workspace.repository;

import eu.appbahn.platform.api.EnvironmentAggregateStatus;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Server-side rollup of {@code resource_cache.status} into a single
 * {@link EnvironmentAggregateStatus} per environment. One SQL round-trip regardless of how many
 * envs are in the listing — listing performance stays O(envs), not O(envs × resources).
 *
 * <p>The CASE expression mirrors the precedence in {@link
 * eu.appbahn.platform.workspace.service.EnvironmentAggregateStatusRollup} — keep the two in sync.
 */
@Repository
public class EnvironmentAggregateStatusRepository {

    private final NamedParameterJdbcTemplate namedJdbc;

    public EnvironmentAggregateStatusRepository(JdbcTemplate jdbcTemplate) {
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    /**
     * Returns the rollup per environment. Envs absent from the result map have zero resources and
     * should be treated as {@link EnvironmentAggregateStatus#UNKNOWN} by the caller.
     */
    public Map<UUID, EnvironmentAggregateStatus> aggregateByEnvironmentIds(Collection<UUID> environmentIds) {
        if (environmentIds == null || environmentIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT environment_id,
                       CASE
                         WHEN bool_or(status = 'ERROR')                            THEN 'FAILED'
                         WHEN bool_or(status IN ('DEGRADED', 'RESTARTING'))        THEN 'DEGRADED'
                         WHEN bool_or(status = 'PENDING')                          THEN 'PENDING'
                         WHEN bool_or(status IN ('READY', 'STOPPED'))              THEN 'HEALTHY'
                         ELSE 'UNKNOWN'
                       END AS aggregate
                FROM resource_cache
                WHERE environment_id IN (:envIds)
                GROUP BY environment_id
                """;
        var params = new MapSqlParameterSource("envIds", environmentIds);
        Map<UUID, EnvironmentAggregateStatus> result = new HashMap<>();
        namedJdbc.query(sql, params, rs -> {
            UUID envId = (UUID) rs.getObject("environment_id");
            String agg = rs.getString("aggregate");
            result.put(envId, EnvironmentAggregateStatus.valueOf(agg));
        });
        return result;
    }
}

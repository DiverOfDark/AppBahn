package eu.appbahn.platform.common.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, AuditLogEntity.AuditLogId> {

    @Query(
            value = "SELECT * FROM audit_log WHERE context @> CAST(:filter AS jsonb) ORDER BY timestamp DESC",
            countQuery = "SELECT count(*) FROM audit_log WHERE context @> CAST(:filter AS jsonb)",
            nativeQuery = true)
    Page<AuditLogEntity> findByContextContaining(String filter, Pageable pageable);

    @Query(value = """
            SELECT * FROM audit_log
            WHERE (CAST(:workspaceId AS text) IS NULL OR context @> CAST('{"workspaceId":"' || :workspaceId || '"}' AS jsonb))
              AND (CAST(:action AS text) IS NULL OR action = :action)
              AND (CAST(:targetType AS text) IS NULL OR target_type = :targetType)
              AND (CAST(:actorId AS uuid) IS NULL OR actor_id = :actorId)
              AND (CAST(:fromTs AS timestamptz) IS NULL OR timestamp >= :fromTs)
              AND (CAST(:toTs AS timestamptz) IS NULL OR timestamp <= :toTs)
            """, countQuery = """
            SELECT count(*) FROM audit_log
            WHERE (CAST(:workspaceId AS text) IS NULL OR context @> CAST('{"workspaceId":"' || :workspaceId || '"}' AS jsonb))
              AND (CAST(:action AS text) IS NULL OR action = :action)
              AND (CAST(:targetType AS text) IS NULL OR target_type = :targetType)
              AND (CAST(:actorId AS uuid) IS NULL OR actor_id = :actorId)
              AND (CAST(:fromTs AS timestamptz) IS NULL OR timestamp >= :fromTs)
              AND (CAST(:toTs AS timestamptz) IS NULL OR timestamp <= :toTs)
            """, nativeQuery = true)
    Page<AuditLogEntity> findFiltered(
            @Param("workspaceId") String workspaceId,
            @Param("action") String action,
            @Param("targetType") String targetType,
            @Param("actorId") UUID actorId,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs,
            Pageable pageable);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM audit_log WHERE timestamp < :cutoff", nativeQuery = true)
    void deleteByTimestampBefore(Instant cutoff);
}

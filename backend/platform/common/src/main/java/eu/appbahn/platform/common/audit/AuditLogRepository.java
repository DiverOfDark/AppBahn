package eu.appbahn.platform.common.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, AuditLogEntity.AuditLogId> {

    @Query(
            value = "SELECT * FROM audit_log WHERE context @> CAST(:filter AS jsonb) ORDER BY timestamp DESC",
            countQuery = "SELECT count(*) FROM audit_log WHERE context @> CAST(:filter AS jsonb)",
            nativeQuery = true)
    Page<AuditLogEntity> findByContextContaining(String filter, Pageable pageable);

    @Query(value = """
            SELECT * FROM audit_log
            WHERE (:workspaceId IS NULL OR context @> CAST('{"workspaceId":"' || :workspaceId || '"}' AS jsonb))
              AND (:action IS NULL OR action = :action)
              AND (:targetType IS NULL OR target_type = :targetType)
              AND (:actorId IS NULL OR actor_id = :actorId)
              AND (:fromTs IS NULL OR timestamp >= :fromTs)
              AND (:toTs IS NULL OR timestamp <= :toTs)
            ORDER BY timestamp DESC
            """, countQuery = """
            SELECT count(*) FROM audit_log
            WHERE (:workspaceId IS NULL OR context @> CAST('{"workspaceId":"' || :workspaceId || '"}' AS jsonb))
              AND (:action IS NULL OR action = :action)
              AND (:targetType IS NULL OR target_type = :targetType)
              AND (:actorId IS NULL OR actor_id = :actorId)
              AND (:fromTs IS NULL OR timestamp >= :fromTs)
              AND (:toTs IS NULL OR timestamp <= :toTs)
            """, nativeQuery = true)
    Page<AuditLogEntity> findFiltered(
            String workspaceId,
            String action,
            String targetType,
            UUID actorId,
            Instant fromTs,
            Instant toTs,
            Pageable pageable);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM audit_log WHERE timestamp < :cutoff", nativeQuery = true)
    void deleteByTimestampBefore(Instant cutoff);
}

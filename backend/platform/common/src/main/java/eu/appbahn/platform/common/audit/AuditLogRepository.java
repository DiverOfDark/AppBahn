package eu.appbahn.platform.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, AuditLogEntity.AuditLogId> {

    @Query(
            value = "SELECT * FROM audit_log WHERE context @> CAST(:filter AS jsonb) ORDER BY timestamp DESC",
            countQuery = "SELECT count(*) FROM audit_log WHERE context @> CAST(:filter AS jsonb)",
            nativeQuery = true)
    Page<AuditLogEntity> findByContextContaining(String filter, Pageable pageable);
}

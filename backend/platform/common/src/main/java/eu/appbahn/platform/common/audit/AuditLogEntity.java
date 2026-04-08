package eu.appbahn.platform.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "audit_log")
@IdClass(AuditLogEntity.AuditLogId.class)
public class AuditLogEntity {

    @Id
    private UUID id;

    @Id
    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(name = "actor_source", nullable = false)
    private String actorSource;

    @Column(nullable = false, length = 63)
    private String action;

    @Column(name = "target_type", nullable = false, length = 63)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String context;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String diff;

    @Column(name = "request_id")
    private String requestId;

    @Data
    public static class AuditLogId implements Serializable {
        private UUID id;
        private Instant timestamp;
    }
}

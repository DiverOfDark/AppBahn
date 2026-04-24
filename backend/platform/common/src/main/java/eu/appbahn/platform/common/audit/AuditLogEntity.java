package eu.appbahn.platform.common.audit;

import eu.appbahn.platform.api.model.AuditAction;
import eu.appbahn.platform.api.model.AuditActorSource;
import eu.appbahn.platform.api.model.AuditDecision;
import eu.appbahn.platform.api.model.AuditFieldChange;
import eu.appbahn.platform.api.model.AuditTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "actor_token_id")
    private UUID actorTokenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_source", nullable = false, length = 32)
    private AuditActorSource actorSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 63)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private AuditTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "environment_id")
    private UUID environmentId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AuditDecision decision;

    @Column(name = "denial_reason", columnDefinition = "TEXT")
    private String denialReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<AuditFieldChange> changes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> details;

    @Column(name = "request_id")
    private String requestId;

    @Data
    public static class AuditLogId implements Serializable {
        private UUID id;
        private Instant timestamp;
    }
}

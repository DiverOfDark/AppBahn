package eu.appbahn.platform.api.tunnel;

import eu.appbahn.platform.api.AuditAction;
import eu.appbahn.platform.api.AuditActorSource;
import eu.appbahn.platform.api.AuditDecision;
import eu.appbahn.platform.api.AuditFieldChange;
import eu.appbahn.platform.api.AuditTargetType;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.lang.Nullable;

/**
 * Operator-originated audit entry (kubectl / GitOps apply/delete). {@code eventId} is the
 * operator-assigned UUIDv7; the platform uses it as the row PK and dedupes on tunnel
 * re-delivery via {@code INSERT … ON CONFLICT DO NOTHING}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLogEvent extends OperatorEvent {

    private UUID eventId;

    @Valid
    private OffsetDateTime timestamp;

    @Nullable
    private UUID actorId;

    @Nullable
    private String actorEmail;

    private AuditActorSource actorSource;
    private AuditAction action;
    private AuditTargetType targetType;

    @Nullable
    private String targetSlug;

    private AuditDecision decision;

    @Nullable
    private String denialReason;

    @Nullable
    private UUID workspaceId;

    @Nullable
    private UUID projectId;

    @Nullable
    private UUID environmentId;

    @Valid
    private List<AuditFieldChange> changes = new ArrayList<>();

    public AuditLogEvent() {
        setType("audit-log");
    }
}

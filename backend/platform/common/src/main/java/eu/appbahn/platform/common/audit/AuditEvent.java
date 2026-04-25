package eu.appbahn.platform.common.audit;

import eu.appbahn.platform.api.AuditAction;
import eu.appbahn.platform.api.AuditActorSource;
import eu.appbahn.platform.api.AuditDecision;
import eu.appbahn.platform.api.AuditFieldChange;
import eu.appbahn.platform.api.AuditTargetType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spring {@code ApplicationEvent} fired by {@link AuditBuilder#save()}. A
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} persists it after the
 * publishing transaction commits; on rollback the event is never delivered and
 * no audit row is written.
 */
public record AuditEvent(
        UUID id,
        Instant timestamp,
        UUID actorId,
        String actorEmail,
        UUID actorTokenId,
        AuditActorSource actorSource,
        AuditAction action,
        AuditTargetType targetType,
        String targetId,
        UUID workspaceId,
        UUID projectId,
        UUID environmentId,
        AuditDecision decision,
        String denialReason,
        List<AuditFieldChange> changes,
        Map<String, String> details,
        String requestId) {}

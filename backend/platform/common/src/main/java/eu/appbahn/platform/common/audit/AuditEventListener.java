package eu.appbahn.platform.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Persists {@link AuditEvent}s published by {@link AuditBuilder#save()}. Fires after the
 * caller's transaction commits — a rollback therefore leaves no audit row, which matches
 * compliance expectations ("audit reflects what actually happened"). {@code fallbackExecution}
 * lets the listener still run when the publisher happens to be outside a transaction.
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditLogRepository repository;

    public AuditEventListener(AuditLogRepository repository) {
        this.repository = repository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(AuditEvent event) {
        try {
            repository.save(toEntity(event));
        } catch (Exception e) {
            log.warn("Failed to persist audit log entry for action {}: {}", event.action(), e.getMessage());
        }
    }

    private static AuditLogEntity toEntity(AuditEvent event) {
        var entry = new AuditLogEntity();
        entry.setId(event.id());
        entry.setTimestamp(event.timestamp());
        entry.setActorId(event.actorId());
        entry.setActorEmail(event.actorEmail());
        entry.setActorTokenId(event.actorTokenId());
        entry.setActorSource(event.actorSource());
        entry.setAction(event.action());
        entry.setTargetType(event.targetType());
        entry.setTargetId(event.targetId());
        entry.setRequestId(event.requestId());
        entry.setWorkspaceId(event.workspaceId());
        entry.setProjectId(event.projectId());
        entry.setEnvironmentId(event.environmentId());
        entry.setDecision(event.decision());
        entry.setDenialReason(event.denialReason());
        entry.setChanges(event.changes());
        entry.setDetails(event.details());
        return entry;
    }
}

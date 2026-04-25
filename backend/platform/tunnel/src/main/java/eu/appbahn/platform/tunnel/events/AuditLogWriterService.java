package eu.appbahn.platform.tunnel.events;

import eu.appbahn.platform.api.tunnel.AuditLogEvent;
import eu.appbahn.platform.common.audit.AuditLogEntity;
import eu.appbahn.platform.common.audit.AuditLogRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists audit entries that originate on the operator side (kubectl / GitOps apply/delete)
 * and arrive via {@code PushEvents}. Tunnel re-delivery is handled by using the operator-
 * assigned {@code eventId} as the row PK and swallowing constraint violations on duplicates.
 */
@Service
public class AuditLogWriterService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogWriterService.class);

    private final AuditLogRepository repository;

    public AuditLogWriterService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Fire-and-forget persist in its own transaction; duplicates from tunnel retries are ignored. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeFromOperator(AuditLogEvent event) {
        try {
            repository.save(toEntity(event));
        } catch (DataIntegrityViolationException dup) {
            // Operator retried a delivery we already accepted — idempotent no-op.
            log.debug("Duplicate operator audit event {} ignored", event.getEventId());
        } catch (Exception e) {
            log.warn(
                    "Failed to persist operator-originated audit entry {} action={}: {}",
                    event.getEventId(),
                    event.getAction(),
                    e.getMessage());
        }
    }

    private static AuditLogEntity toEntity(AuditLogEvent event) {
        var entry = new AuditLogEntity();
        entry.setId(event.getEventId() != null ? event.getEventId() : UUID.randomUUID());
        entry.setTimestamp(event.getTimestamp() != null ? event.getTimestamp().toInstant() : Instant.now());
        entry.setActorId(event.getActorId());
        entry.setActorEmail(emptyToNull(event.getActorEmail()));
        entry.setActorSource(event.getActorSource());
        entry.setAction(event.getAction());
        entry.setTargetType(event.getTargetType());
        entry.setTargetId(event.getTargetSlug());
        entry.setWorkspaceId(event.getWorkspaceId());
        entry.setProjectId(event.getProjectId());
        entry.setEnvironmentId(event.getEnvironmentId());
        entry.setDecision(event.getDecision());
        entry.setDenialReason(emptyToNull(event.getDenialReason()));
        if (event.getChanges() != null && !event.getChanges().isEmpty()) {
            entry.setChanges(new ArrayList<>(event.getChanges()));
        }
        entry.setDetails(new LinkedHashMap<>());
        return entry;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}

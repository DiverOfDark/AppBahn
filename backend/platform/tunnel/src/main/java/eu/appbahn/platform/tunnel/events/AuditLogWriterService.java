package eu.appbahn.platform.tunnel.events;

import eu.appbahn.platform.common.audit.AuditLogEntity;
import eu.appbahn.platform.common.audit.AuditLogRepository;
import eu.appbahn.tunnel.v1.AuditLogEvent;
import eu.appbahn.tunnel.wire.AuditEnumMapper;
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
 * assigned {@code event_id} as the row PK and swallowing constraint violations on duplicates.
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
        entry.setId(parseUuid(event.getEventId(), UUID.randomUUID()));
        entry.setTimestamp(toInstant(event));
        entry.setActorId(parseUuidOrNull(event.getActorId()));
        entry.setActorEmail(emptyToNull(event.getActorEmail()));
        entry.setActorSource(AuditEnumMapper.fromProto(event.getActorSource()));
        entry.setAction(AuditEnumMapper.fromProto(event.getAction()));
        entry.setTargetType(AuditEnumMapper.fromProto(event.getTargetType()));
        entry.setTargetId(event.getTargetSlug());
        entry.setWorkspaceId(parseUuidOrNull(event.getWorkspaceId()));
        entry.setProjectId(parseUuidOrNull(event.getProjectId()));
        entry.setEnvironmentId(parseUuidOrNull(event.getEnvironmentId()));
        entry.setDecision(AuditEnumMapper.fromProto(event.getDecision()));
        entry.setDenialReason(emptyToNull(event.getDenialReason()));
        if (!event.getChangesList().isEmpty()) {
            var changes = new ArrayList<eu.appbahn.platform.api.model.AuditFieldChange>(
                    event.getChangesList().size());
            for (var c : event.getChangesList()) {
                changes.add(new eu.appbahn.platform.api.model.AuditFieldChange(c.getField())
                        .oldValue(emptyToNull(c.getOldValue()))
                        .newValue(emptyToNull(c.getNewValue())));
            }
            entry.setChanges(changes);
        }
        entry.setDetails(new LinkedHashMap<>());
        return entry;
    }

    private static Instant toInstant(AuditLogEvent event) {
        if (!event.hasTimestamp()) {
            return Instant.now();
        }
        var ts = event.getTimestamp();
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
    }

    private static UUID parseUuid(String s, UUID fallback) {
        try {
            return s == null || s.isBlank() ? fallback : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static UUID parseUuidOrNull(String s) {
        try {
            return s == null || s.isBlank() ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}

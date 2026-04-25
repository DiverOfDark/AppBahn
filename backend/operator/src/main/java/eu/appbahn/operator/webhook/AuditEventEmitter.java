package eu.appbahn.operator.webhook;

import eu.appbahn.operator.tunnel.OperatorEventPublisher;
import eu.appbahn.operator.tunnel.client.model.AuditAction;
import eu.appbahn.operator.tunnel.client.model.AuditActorSource;
import eu.appbahn.operator.tunnel.client.model.AuditDecision;
import eu.appbahn.operator.tunnel.client.model.AuditLogEvent;
import eu.appbahn.operator.tunnel.client.model.AuditTargetType;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Builds and emits {@link AuditLogEvent}s for kubectl / GitOps-originated Resource CR
 * admissions — both allowed and denied. Called from {@link ResourceAdmissionController}
 * after loop-prevention filters (operator SA username, dry-run, self-change fingerprint)
 * have decided the event is worth recording.
 *
 * <p>Emission is best-effort: tunnel failures log a warning and move on, since admission
 * must respond within K8s's 10s budget and the CR has already been decided on.
 */
@Component
public class AuditEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(AuditEventEmitter.class);

    private final OperatorEventPublisher eventPublisher;

    public AuditEventEmitter(OperatorEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void emitAllow(AdmissionRequest request, String targetSlug) {
        emit(request, targetSlug, AuditDecision.ALLOWED, "");
    }

    public void emitDeny(AdmissionRequest request, String targetSlug, String reason) {
        emit(request, targetSlug, AuditDecision.DENIED, reason == null ? "" : reason);
    }

    private void emit(AdmissionRequest request, String targetSlug, AuditDecision decision, String denialReason) {
        try {
            var actor = request.getUserInfo();
            var event = new AuditLogEvent();
            event.setEventId(UUID.randomUUID());
            event.setTimestamp(OffsetDateTime.now());
            event.setActorEmail(actor != null && actor.getUsername() != null ? actor.getUsername() : "");
            event.setActorSource(AuditActorSource.KUBECTL);
            event.setAction(actionFor(request.getOperation()));
            event.setTargetType(AuditTargetType.RESOURCE);
            event.setTargetSlug(targetSlug == null ? "" : targetSlug);
            event.setDecision(decision);
            event.setDenialReason(denialReason);
            eventPublisher.emit(event);
        } catch (Exception e) {
            log.warn(
                    "Failed to emit operator audit log event for {} on {}: {}",
                    request.getOperation(),
                    targetSlug,
                    e.getMessage());
        }
    }

    private static AuditAction actionFor(String operation) {
        if (operation == null) {
            return null;
        }
        return switch (operation.toUpperCase()) {
            case "CREATE" -> AuditAction.RESOURCE_CREATED;
            case "UPDATE" -> AuditAction.RESOURCE_UPDATED;
            case "DELETE" -> AuditAction.RESOURCE_DELETED;
            default -> null;
        };
    }
}

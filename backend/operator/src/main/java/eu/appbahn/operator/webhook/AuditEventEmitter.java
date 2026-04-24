package eu.appbahn.operator.webhook;

import com.google.protobuf.Timestamp;
import eu.appbahn.operator.tunnel.OperatorEventPublisher;
import eu.appbahn.tunnel.v1.AuditAction;
import eu.appbahn.tunnel.v1.AuditActorSource;
import eu.appbahn.tunnel.v1.AuditDecision;
import eu.appbahn.tunnel.v1.AuditLogEvent;
import eu.appbahn.tunnel.v1.AuditTargetType;
import eu.appbahn.tunnel.v1.OperatorEvent;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import java.time.Instant;
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
        emit(request, targetSlug, AuditDecision.AUDIT_DECISION_ALLOWED, "");
    }

    public void emitDeny(AdmissionRequest request, String targetSlug, String reason) {
        emit(request, targetSlug, AuditDecision.AUDIT_DECISION_DENIED, reason == null ? "" : reason);
    }

    private void emit(AdmissionRequest request, String targetSlug, AuditDecision decision, String denialReason) {
        try {
            var actor = request.getUserInfo();
            var eventBuilder = AuditLogEvent.newBuilder()
                    .setEventId(UUID.randomUUID().toString())
                    .setTimestamp(nowTimestamp())
                    .setActorEmail(actor != null && actor.getUsername() != null ? actor.getUsername() : "")
                    .setActorSource(AuditActorSource.AUDIT_ACTOR_SOURCE_KUBECTL)
                    .setAction(actionFor(request.getOperation()))
                    .setTargetType(AuditTargetType.AUDIT_TARGET_TYPE_RESOURCE)
                    .setTargetSlug(targetSlug == null ? "" : targetSlug)
                    .setDecision(decision)
                    .setDenialReason(denialReason);
            eventPublisher.emit(
                    OperatorEvent.newBuilder().setAuditLog(eventBuilder).build());
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
            return AuditAction.AUDIT_ACTION_UNSPECIFIED;
        }
        return switch (operation.toUpperCase()) {
            case "CREATE" -> AuditAction.AUDIT_ACTION_RESOURCE_CREATED;
            case "UPDATE" -> AuditAction.AUDIT_ACTION_RESOURCE_UPDATED;
            case "DELETE" -> AuditAction.AUDIT_ACTION_RESOURCE_DELETED;
            default -> AuditAction.AUDIT_ACTION_UNSPECIFIED;
        };
    }

    private static Timestamp nowTimestamp() {
        Instant now = Instant.now();
        return Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }
}

package eu.appbahn.platform.common.audit;

import eu.appbahn.platform.api.model.AuditAction;
import eu.appbahn.platform.api.model.AuditDecision;
import eu.appbahn.platform.api.model.AuditFieldChange;
import eu.appbahn.platform.api.model.AuditTargetType;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.shared.util.UuidV7;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Fluent builder for an audit entry. Obtain via {@link AuditLogService#audit(AuthContext, AuditAction)}.
 * Required chains: {@link #target(AuditTargetType, String)} and {@link #inWorkspace(UUID)}.
 * Terminal {@link #save()} publishes an {@link AuditEvent}; an {@code @TransactionalEventListener}
 * persists it after the caller's transaction commits (no row written on rollback).
 */
public final class AuditBuilder {

    private final ApplicationEventPublisher publisher;
    private final AuthContext actor;
    private final AuditAction action;

    private AuditTargetType targetType;
    private String targetId;
    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private final List<AuditFieldChange> changes = new ArrayList<>();
    private final Map<String, String> details = new LinkedHashMap<>();

    AuditBuilder(ApplicationEventPublisher publisher, AuthContext actor, AuditAction action) {
        this.publisher = publisher;
        this.actor = actor;
        this.action = action;
    }

    public AuditBuilder target(AuditTargetType type, String slug) {
        this.targetType = type;
        this.targetId = slug;
        return this;
    }

    public AuditBuilder inWorkspace(UUID workspaceId) {
        this.workspaceId = workspaceId;
        return this;
    }

    public AuditBuilder inProject(UUID projectId) {
        this.projectId = projectId;
        return this;
    }

    public AuditBuilder inEnvironment(UUID environmentId) {
        this.environmentId = environmentId;
        return this;
    }

    /** Append a field-level change. Values are coerced to string for uniform presentation. */
    public AuditBuilder change(String field, Object oldValue, Object newValue) {
        return change(AuditLogService.change(field, oldValue, newValue));
    }

    public AuditBuilder change(AuditFieldChange fieldChange) {
        this.changes.add(fieldChange);
        return this;
    }

    public AuditBuilder changes(AuditFieldChange... fieldChanges) {
        for (AuditFieldChange c : fieldChanges) {
            this.changes.add(c);
        }
        return this;
    }

    /** Append one piece of flat event metadata. */
    public AuditBuilder detail(String key, String value) {
        this.details.put(key, value);
        return this;
    }

    public AuditBuilder details(Map<String, String> entries) {
        this.details.putAll(entries);
        return this;
    }

    public void save() {
        Objects.requireNonNull(targetType, "target(type, slug) must be called before save()");
        Objects.requireNonNull(targetId, "target(type, slug) must be called before save()");

        publisher.publishEvent(new AuditEvent(
                UuidV7.generate(),
                Instant.now(),
                actor.userId(),
                actor.email(),
                actor.tokenId(),
                actor.actorSource(),
                action,
                targetType,
                targetId,
                workspaceId,
                projectId,
                environmentId,
                AuditDecision.ALLOWED,
                null,
                changes.isEmpty() ? null : List.copyOf(changes),
                details.isEmpty() ? null : Map.copyOf(details),
                MDC.get("requestId")));
    }
}

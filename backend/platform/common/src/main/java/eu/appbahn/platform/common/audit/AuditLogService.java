package eu.appbahn.platform.common.audit;

import eu.appbahn.platform.api.AuditAction;
import eu.appbahn.platform.api.AuditFieldChange;
import eu.appbahn.platform.api.AuditLogEntry;
import eu.appbahn.platform.api.PagedAuditLogResponse;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.common.util.PaginationUtil;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;
    private final ApplicationEventPublisher publisher;

    public AuditLogService(AuditLogRepository repository, ApplicationEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    /**
     * Start building an audit entry. Chain {@link AuditBuilder#target}, optionally
     * {@link AuditBuilder#inWorkspace}, {@link AuditBuilder#change}, {@link AuditBuilder#detail},
     * and finish with {@link AuditBuilder#save()}.
     */
    public AuditBuilder audit(AuthContext actor, AuditAction action) {
        return new AuditBuilder(publisher, actor, action);
    }

    /** Build an {@link AuditFieldChange}; non-null values are coerced to string for uniform presentation. */
    public static AuditFieldChange change(String field, Object oldValue, Object newValue) {
        AuditFieldChange c = new AuditFieldChange();
        c.setField(field);
        c.setOldValue(oldValue != null ? oldValue.toString() : null);
        c.setNewValue(newValue != null ? newValue.toString() : null);
        return c;
    }

    public PagedAuditLogResponse query(
            UUID workspaceId,
            String action,
            String targetType,
            UUID actorId,
            Instant from,
            Instant to,
            int page,
            int size) {
        var pageable = PaginationUtil.toPageable(page, size, null, Sort.by(Sort.Direction.DESC, "timestamp"));
        var result = repository.findFiltered(
                workspaceId != null ? workspaceId.toString() : null, action, targetType, actorId, from, to, pageable);

        var entries = result.getContent().stream().map(this::toAuditLogEntry).toList();

        var response = new PagedAuditLogResponse();
        response.setContent(entries);
        response.setPage(result.getNumber());
        response.setSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        return response;
    }

    private AuditLogEntry toAuditLogEntry(AuditLogEntity e) {
        var entry = new AuditLogEntry();
        entry.setId(e.getId());
        entry.setTimestamp(e.getTimestamp().atOffset(ZoneOffset.UTC));
        entry.setActorId(e.getActorId());
        entry.setActorEmail(e.getActorEmail());
        entry.setActorTokenId(e.getActorTokenId());
        entry.setActorSource(e.getActorSource());
        entry.setAction(e.getAction());
        entry.setTargetType(e.getTargetType());
        entry.setTargetId(e.getTargetId());
        entry.setRequestId(e.getRequestId());
        entry.setWorkspaceId(e.getWorkspaceId());
        entry.setProjectId(e.getProjectId());
        entry.setEnvironmentId(e.getEnvironmentId());
        entry.setDecision(e.getDecision());
        entry.setDenialReason(e.getDenialReason());
        if (e.getChanges() != null) {
            entry.setChanges(e.getChanges());
        }
        if (e.getDetails() != null) {
            entry.setDetails(e.getDetails());
        }
        return entry;
    }
}

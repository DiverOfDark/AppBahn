package eu.appbahn.platform.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.model.AuditLogEntry;
import eu.appbahn.platform.api.model.PagedAuditLogResponse;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.common.util.PaginationUtil;
import eu.appbahn.shared.util.UuidV7;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
            AuthContext actor,
            String action,
            String targetType,
            String targetSlug,
            UUID workspaceId,
            Map<String, Object> diff) {
        var entry = new AuditLogEntity();
        entry.setId(UuidV7.generate());
        entry.setTimestamp(Instant.now());
        entry.setActorId(actor.userId());
        entry.setActorEmail(actor.email());
        entry.setActorSource("api");
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetSlug);
        entry.setRequestId(MDC.get("requestId"));

        var ctx = new LinkedHashMap<String, Object>();
        if (workspaceId != null) {
            ctx.put("workspaceId", workspaceId.toString());
        }
        if (targetSlug != null) {
            ctx.put("targetSlug", targetSlug);
        }
        if (!ctx.isEmpty()) {
            entry.setContext(toJson(ctx));
        }
        if (diff != null && !diff.isEmpty()) {
            entry.setDiff(toJson(diff));
        }

        try {
            repository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write audit log entry for action {}: {}", action, e.getMessage());
        }
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
        entry.setActorSource(e.getActorSource());
        entry.setAction(e.getAction());
        entry.setTargetType(e.getTargetType());
        entry.setTargetId(e.getTargetId());
        entry.setRequestId(e.getRequestId());
        entry.setContext(parseJson(e.getContext()));
        entry.setDiff(parseJson(e.getDiff()));
        return entry;
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON: {}", e.getMessage());
            return null;
        }
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}

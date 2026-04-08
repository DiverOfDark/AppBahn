package eu.appbahn.platform.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.shared.util.UuidV7;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}

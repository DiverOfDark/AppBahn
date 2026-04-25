package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class AuditLogEntry {

    @Valid
    @Nullable
    private UUID id;

    @Valid
    @Nullable
    private OffsetDateTime timestamp;

    @Valid
    @Nullable
    private UUID actorId;

    @Nullable
    private String actorEmail;

    @Valid
    @Nullable
    private UUID actorTokenId;

    @Valid
    @Nullable
    private AuditActorSource actorSource;

    @Valid
    @Nullable
    private AuditAction action;

    @Valid
    @Nullable
    private AuditTargetType targetType;

    @Nullable
    private String targetId;

    @Valid
    @Nullable
    private UUID workspaceId;

    @Valid
    @Nullable
    private UUID projectId;

    @Valid
    @Nullable
    private UUID environmentId;

    @Valid
    @Nullable
    private AuditDecision decision;

    @Nullable
    private String denialReason;

    @Valid
    private List<AuditFieldChange> changes = new ArrayList<>();

    private Map<String, String> details = new HashMap<>();

    @Nullable
    private String requestId;
}

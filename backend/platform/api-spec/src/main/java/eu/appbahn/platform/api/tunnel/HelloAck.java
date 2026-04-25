package eu.appbahn.platform.api.tunnel;

import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * First frame on every fresh {@code SubscribeCommands} stream. Carries the session id plus —
 * if the operator's echoed revisions are stale — the initial admin-config + quota/RBAC
 * snapshots so the admission webhook is never in a "no data" state.
 */
@Data
public class HelloAck {

    /** SSE {@code event:} name that carries this payload. */
    public static final String EVENT_NAME = "hello-ack";

    private String sessionId;

    @Valid
    @Nullable
    private AdminConfigPush adminConfig;

    @Valid
    @Nullable
    private QuotaRbacCachePush quotaRbac;
}

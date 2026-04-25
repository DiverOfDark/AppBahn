package eu.appbahn.platform.api.tunnel;

import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class QuotaRbacCachePush {

    /** SSE {@code event:} name that carries this payload. */
    public static final String EVENT_NAME = "quota-rbac-cache-push";

    private long revision;

    @Valid
    @Nullable
    private QuotaRbacSnapshot snapshot;
}

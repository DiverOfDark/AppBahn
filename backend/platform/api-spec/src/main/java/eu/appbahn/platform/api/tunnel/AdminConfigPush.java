package eu.appbahn.platform.api.tunnel;

import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class AdminConfigPush {

    /** SSE {@code event:} name that carries this payload. */
    public static final String EVENT_NAME = "admin-config-push";

    private long revision;

    @Valid
    @Nullable
    private AdminConfigSnapshot snapshot;
}

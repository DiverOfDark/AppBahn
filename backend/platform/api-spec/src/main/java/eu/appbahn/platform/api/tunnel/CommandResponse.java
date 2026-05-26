package eu.appbahn.platform.api.tunnel;

import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class CommandResponse {

    private CommandStatus status;

    @Nullable
    private String message;

    /**
     * Optional typed body for query-style commands ({@code list-pods},
     * {@code query-cluster-capacity}). Action commands (apply/delete) leave this null.
     */
    @Valid
    @Nullable
    private CommandResponsePayload payload;
}

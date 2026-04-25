package eu.appbahn.platform.api.tunnel;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class CommandResponse {

    private CommandStatus status;

    @Nullable
    private String message;
}

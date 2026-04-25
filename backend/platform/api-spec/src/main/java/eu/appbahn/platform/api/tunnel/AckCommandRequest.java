package eu.appbahn.platform.api.tunnel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AckCommandRequest {

    @Valid
    @NotNull
    private CommandResponse response;
}

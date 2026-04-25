package eu.appbahn.platform.api.resource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class RollbackRequest {

    @NotNull
    @Valid
    private UUID deploymentId;
}

package eu.appbahn.platform.api.workspace;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateWorkspaceRequest {

    @NotNull
    private String name;
}

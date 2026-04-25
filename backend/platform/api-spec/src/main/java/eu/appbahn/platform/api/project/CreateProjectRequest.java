package eu.appbahn.platform.api.project;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateProjectRequest {

    @NotNull
    private String name;

    @NotNull
    private String workspaceSlug;
}

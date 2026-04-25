package eu.appbahn.platform.api.workspace;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class UpdateWorkspaceRequest {

    @Nullable
    private String name;
}

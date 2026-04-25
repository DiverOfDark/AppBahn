package eu.appbahn.platform.api.project;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class UpdateProjectRequest {

    @Nullable
    private String name;
}

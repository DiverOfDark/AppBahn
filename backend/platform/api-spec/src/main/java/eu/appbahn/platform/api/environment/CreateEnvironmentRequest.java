package eu.appbahn.platform.api.environment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class CreateEnvironmentRequest {

    @NotNull
    private String name;

    @NotNull
    private String projectSlug;

    @Nullable
    private String description;

    @Nullable
    private String targetCluster;
}

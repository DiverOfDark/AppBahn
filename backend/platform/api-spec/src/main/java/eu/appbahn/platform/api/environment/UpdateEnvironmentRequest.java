package eu.appbahn.platform.api.environment;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class UpdateEnvironmentRequest {

    @Nullable
    private String name;

    @Nullable
    private String description;
}

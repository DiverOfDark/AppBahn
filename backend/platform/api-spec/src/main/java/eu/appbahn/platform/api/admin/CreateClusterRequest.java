package eu.appbahn.platform.api.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class CreateClusterRequest {

    @NotNull
    private String name;

    @Nullable
    private String description;

    @NotNull
    private String kubeconfigSecret;
}

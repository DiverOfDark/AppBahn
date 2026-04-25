package eu.appbahn.platform.api.environment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetTargetClusterRequest {

    @NotNull
    private String clusterName;
}

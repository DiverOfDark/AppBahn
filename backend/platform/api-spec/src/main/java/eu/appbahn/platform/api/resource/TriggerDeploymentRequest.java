package eu.appbahn.platform.api.resource;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class TriggerDeploymentRequest {

    @Nullable
    private String sourceRef;
}

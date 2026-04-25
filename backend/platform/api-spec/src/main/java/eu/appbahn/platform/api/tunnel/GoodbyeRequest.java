package eu.appbahn.platform.api.tunnel;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class GoodbyeRequest {

    @NotBlank
    private String clusterName;

    @Nullable
    private String sessionId;

    @Nullable
    private String reason;
}

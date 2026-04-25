package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class DeploymentApproval {

    @Valid
    @Nullable
    private UUID id;

    @Valid
    @Nullable
    private UUID deploymentId;

    @Valid
    @Nullable
    private UUID userId;

    @Nullable
    private String decision;

    @Valid
    @Nullable
    private OffsetDateTime updatedAt;
}

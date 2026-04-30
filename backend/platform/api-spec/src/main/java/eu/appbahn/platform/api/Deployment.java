package eu.appbahn.platform.api;

import eu.appbahn.shared.crd.imagesource.BuildLifecycle;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class Deployment {

    @Valid
    @Nullable
    private UUID id;

    @Nullable
    private String resourceSlug;

    @Nullable
    private String environmentSlug;

    @Nullable
    private String sourceRef;

    @Nullable
    private String imageRef;

    @Nullable
    private TriggerType triggeredBy;

    @Nullable
    private BuildLifecycle lifecycle;

    @Nullable
    private Boolean isPrimary;

    @Valid
    @Nullable
    private UUID sourceDeploymentId;

    @Valid
    @Nullable
    private OffsetDateTime createdAt;

    @Valid
    @Nullable
    private OffsetDateTime updatedAt;
}

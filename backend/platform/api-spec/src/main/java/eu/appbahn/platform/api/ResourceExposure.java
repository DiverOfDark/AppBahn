package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class ResourceExposure {

    @Valid
    @Nullable
    private UUID id;

    @Nullable
    private String resourceSlug;

    @Nullable
    private Integer port;

    @Nullable
    private Integer externalPort;

    @Valid
    @Nullable
    private OffsetDateTime expiresAt;

    @Valid
    @Nullable
    private UUID createdBy;
}

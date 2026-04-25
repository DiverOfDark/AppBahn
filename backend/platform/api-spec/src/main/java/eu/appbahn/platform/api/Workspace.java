package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class Workspace {

    @Valid
    @Nullable
    private UUID id;

    @Nullable
    private String name;

    @Nullable
    private String slug;

    @Valid
    @Nullable
    private Quota quota;

    @Valid
    @Nullable
    private RegistryConfig registry;

    @Nullable
    private String runtimeClassName;

    @Valid
    @Nullable
    private OffsetDateTime createdAt;

    @Valid
    @Nullable
    private OffsetDateTime updatedAt;
}

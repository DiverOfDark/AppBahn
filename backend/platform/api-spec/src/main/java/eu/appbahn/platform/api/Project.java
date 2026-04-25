package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class Project {

    @Valid
    @Nullable
    private UUID id;

    @Nullable
    private String name;

    @Nullable
    private String slug;

    @Nullable
    private String workspaceSlug;

    @Valid
    @Nullable
    private Quota quota;

    @Valid
    @Nullable
    private RegistryConfig registry;

    @Valid
    @Nullable
    private OffsetDateTime createdAt;

    @Valid
    @Nullable
    private OffsetDateTime updatedAt;
}

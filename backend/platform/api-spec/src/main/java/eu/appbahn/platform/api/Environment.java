package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class Environment {

    @Valid
    @Nullable
    private UUID id;

    @Nullable
    private String name;

    @Nullable
    private String slug;

    @Nullable
    private String description;

    @Nullable
    private String projectSlug;

    @Nullable
    private String targetCluster;

    @Valid
    @Nullable
    private ApprovalGatesConfig approvalGates;

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

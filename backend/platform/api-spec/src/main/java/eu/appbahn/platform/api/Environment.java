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

    /**
     * Server-side rollup of this environment's child resource statuses. Populated on the listing
     * endpoint so the console can render a status dot per env tab without fetching every resource;
     * left {@code null} on single-environment fetches where the caller already drills into the
     * resources. See {@link EnvironmentAggregateStatus} for precedence rules.
     */
    @Nullable
    private EnvironmentAggregateStatus aggregateStatus;

    @Valid
    @Nullable
    private OffsetDateTime createdAt;

    @Valid
    @Nullable
    private OffsetDateTime updatedAt;
}

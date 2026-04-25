package eu.appbahn.platform.api;

import eu.appbahn.shared.model.MemberRole;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class EnvironmentToken {

    @Valid
    @Nullable
    private UUID id;

    @Nullable
    private String name;

    @Valid
    @Nullable
    private MemberRole role;

    @Valid
    @Nullable
    private OffsetDateTime expiresAt;

    @Valid
    @Nullable
    private OffsetDateTime lastUsedAt;

    @Nullable
    private String createdBy;

    @Valid
    @Nullable
    private OffsetDateTime createdAt;
}

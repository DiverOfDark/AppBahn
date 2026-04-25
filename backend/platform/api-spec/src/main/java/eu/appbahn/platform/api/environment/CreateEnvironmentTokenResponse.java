package eu.appbahn.platform.api.environment;

import eu.appbahn.shared.model.MemberRole;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class CreateEnvironmentTokenResponse {

    @Valid
    @Nullable
    private UUID id;

    @Nullable
    private String name;

    @Nullable
    private String token;

    @Valid
    @Nullable
    private MemberRole role;

    @Valid
    @Nullable
    private OffsetDateTime expiresAt;
}

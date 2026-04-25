package eu.appbahn.platform.api;

import eu.appbahn.shared.model.MemberRole;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class OidcGroupMapping {

    @Valid
    @Nullable
    private UUID id;

    @Nullable
    private String oidcGroup;

    @Valid
    @Nullable
    private UUID workspaceId;

    @Valid
    @Nullable
    private MemberRole role;
}

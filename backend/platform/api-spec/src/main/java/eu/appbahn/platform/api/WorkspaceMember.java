package eu.appbahn.platform.api;

import eu.appbahn.shared.model.MemberRole;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class WorkspaceMember {

    @Valid
    @Nullable
    private UUID userId;

    @Nullable
    private String email;

    @Valid
    @Nullable
    private MemberRole role;

    @Valid
    @Nullable
    private MemberStatus status;
}

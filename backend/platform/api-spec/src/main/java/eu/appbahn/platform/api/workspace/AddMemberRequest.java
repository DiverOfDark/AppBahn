package eu.appbahn.platform.api.workspace;

import eu.appbahn.shared.model.MemberRole;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class AddMemberRequest {

    @Nullable
    private String email;

    @Valid
    @Nullable
    private MemberRole role;
}

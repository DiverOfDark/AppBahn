package eu.appbahn.platform.api;

import eu.appbahn.shared.model.MemberRole;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class UpdateMemberRequest {

    @Valid
    @Nullable
    private MemberRole role;
}

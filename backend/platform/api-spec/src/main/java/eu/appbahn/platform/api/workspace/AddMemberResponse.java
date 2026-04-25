package eu.appbahn.platform.api.workspace;

import eu.appbahn.platform.api.MemberStatus;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class AddMemberResponse {

    @Valid
    @Nullable
    private MemberStatus status;
}

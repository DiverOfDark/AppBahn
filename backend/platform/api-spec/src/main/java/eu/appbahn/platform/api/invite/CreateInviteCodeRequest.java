package eu.appbahn.platform.api.invite;

import eu.appbahn.shared.model.MemberRole;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class CreateInviteCodeRequest {

    @NotNull
    private MemberRole role;

    @Nullable
    private Instant expiresAt;

    @Min(1)
    private int maxUses = 1;
}

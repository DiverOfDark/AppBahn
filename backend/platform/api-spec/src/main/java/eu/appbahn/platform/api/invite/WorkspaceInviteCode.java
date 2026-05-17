package eu.appbahn.platform.api.invite;

import eu.appbahn.shared.model.MemberRole;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class WorkspaceInviteCode {

    private UUID id;

    private String code;

    private MemberRole role;

    @Nullable
    private Instant expiresAt;

    private int maxUses;

    private int useCount;

    private Instant createdAt;

    @Nullable
    private UUID createdBy;
}

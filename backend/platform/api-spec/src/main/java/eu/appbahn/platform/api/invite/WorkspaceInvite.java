package eu.appbahn.platform.api.invite;

import eu.appbahn.shared.model.MemberRole;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class WorkspaceInvite {

    private UUID id;

    private String workspaceSlug;

    private String workspaceName;

    private MemberRole role;

    @Nullable
    private InvitedBy invitedBy;

    private Instant invitedAt;

    @Nullable
    private Instant expiresAt;

    @Data
    public static class InvitedBy {
        private UUID id;
        private String name;
        private String email;
    }
}

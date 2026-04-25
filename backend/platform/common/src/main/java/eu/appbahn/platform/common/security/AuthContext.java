package eu.appbahn.platform.common.security;

import eu.appbahn.platform.api.AuditActorSource;
import java.util.List;
import java.util.UUID;

public record AuthContext(
        UUID userId,
        String email,
        List<String> groups,
        boolean platformAdmin,
        AuditActorSource actorSource,
        UUID tokenId) {

    /** Convenience overload for API-authenticated callers (JWT / session / integration tests). */
    public AuthContext(UUID userId, String email, List<String> groups, boolean platformAdmin) {
        this(userId, email, groups, platformAdmin, AuditActorSource.API, null);
    }
}

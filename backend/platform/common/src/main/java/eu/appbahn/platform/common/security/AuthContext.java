package eu.appbahn.platform.common.security;

import java.util.List;
import java.util.UUID;

public record AuthContext(
        UUID userId,
        String email,
        List<String> groups,
        boolean platformAdmin
) {
}

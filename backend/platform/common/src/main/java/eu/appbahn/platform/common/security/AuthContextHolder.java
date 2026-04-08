package eu.appbahn.platform.common.security;

import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthContextHolder {

    private AuthContextHolder() {}

    public static AuthContext get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AppBahnAuthenticationToken token) {
            return token.getPrincipal();
        }
        throw new IllegalStateException("No AuthContext available in SecurityContext");
    }
}

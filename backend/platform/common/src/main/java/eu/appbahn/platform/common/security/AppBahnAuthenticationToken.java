package eu.appbahn.platform.common.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

public class AppBahnAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthContext authContext;

    public AppBahnAuthenticationToken(AuthContext authContext) {
        super(List.of()); // No Spring authorities — permissions are handled by PermissionService
        this.authContext = authContext;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AuthContext getPrincipal() {
        return authContext;
    }
}

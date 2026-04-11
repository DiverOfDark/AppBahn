package eu.appbahn.platform.common.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class AppBahnAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthContext authContext;

    public AppBahnAuthenticationToken(AuthContext authContext) {
        this(authContext, List.of());
    }

    public AppBahnAuthenticationToken(AuthContext authContext, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
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

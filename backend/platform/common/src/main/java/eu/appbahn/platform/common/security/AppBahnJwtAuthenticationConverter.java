package eu.appbahn.platform.common.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

public class AppBahnJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final BiFunction<Jwt, List<String>, AuthContext> authContextResolver;
    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    public AppBahnJwtAuthenticationConverter(BiFunction<Jwt, List<String>, AuthContext> authContextResolver) {
        this.authContextResolver = authContextResolver;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<String> groups = jwt.getClaimAsStringList("groups");
        if (groups == null) {
            groups = Collections.emptyList();
        }
        AuthContext ctx = authContextResolver.apply(jwt, groups);
        Collection<GrantedAuthority> authorities = scopeConverter.convert(jwt);
        return new AppBahnAuthenticationToken(ctx, authorities != null ? authorities : List.of());
    }
}

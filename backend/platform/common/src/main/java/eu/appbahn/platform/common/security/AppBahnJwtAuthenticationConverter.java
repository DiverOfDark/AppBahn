package eu.appbahn.platform.common.security;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

public class AppBahnJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final BiFunction<Jwt, List<String>, AuthContext> authContextResolver;

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
        return new AppBahnAuthenticationToken(ctx);
    }
}

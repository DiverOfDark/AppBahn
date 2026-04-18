package eu.appbahn.platform.user.config;

import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.user.service.UserService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;

@Configuration
public class AuthContextConfig {

    /** Scope granted to service tokens (client_credentials) that represent platform components. */
    static final String INTERNAL_SCOPE = "internal";

    @Value("${platform.admin-groups:}")
    private String adminGroups;

    @Bean
    public BiFunction<Jwt, List<String>, AuthContext> authContextResolver(UserService userService) {
        Set<String> adminGroupSet = parseGroups(adminGroups);
        return (jwt, groups) -> {
            // Service tokens (client_credentials with 'internal' scope) don't have user context.
            // We parse the scope claim into a set and check exact membership so that lookalikes
            // like "internal-audit" or "notinternal" do not accidentally bypass user provisioning.
            if (parseScopes(jwt.getClaimAsString("scope")).contains(INTERNAL_SCOPE)) {
                // Synthetic admin context: downstream permission checks must short-circuit on
                // platformAdmin=true without ever hitting repositories keyed by userId.
                return new AuthContext(null, null, List.of(), true);
            }
            var user = userService.findOrCreateFromJwt(jwt);
            boolean isPlatformAdmin =
                    !adminGroupSet.isEmpty() && groups.stream().anyMatch(adminGroupSet::contains);
            return new AuthContext(user.getId(), user.getEmail(), groups, isPlatformAdmin);
        };
    }

    private Set<String> parseGroups(String groups) {
        if (groups == null || groups.isBlank()) {
            return Collections.emptySet();
        }
        return Set.of(groups.split(","));
    }

    private static Set<String> parseScopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(scope.split("\\s+")).filter(s -> !s.isBlank()).collect(Collectors.toUnmodifiableSet());
    }
}

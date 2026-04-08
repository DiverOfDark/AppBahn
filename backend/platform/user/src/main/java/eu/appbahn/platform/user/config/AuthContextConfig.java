package eu.appbahn.platform.user.config;

import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.user.service.UserService;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;

@Configuration
public class AuthContextConfig {

    @Value("${platform.admin-groups:}")
    private String adminGroups;

    @Bean
    public BiFunction<Jwt, List<String>, AuthContext> authContextResolver(UserService userService) {
        Set<String> adminGroupSet = parseGroups(adminGroups);
        return (jwt, groups) -> {
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
}

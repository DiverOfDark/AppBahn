package eu.appbahn.platform.workspace.config;

import eu.appbahn.platform.workspace.service.EnvironmentTokenAuthService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.function.Function;

@Configuration
public class EnvironmentTokenAuthConfig {

    @Bean
    public Function<String, Optional<Authentication>> environmentTokenAuthenticator(
            EnvironmentTokenAuthService authService
    ) {
        return authService::authenticate;
    }
}

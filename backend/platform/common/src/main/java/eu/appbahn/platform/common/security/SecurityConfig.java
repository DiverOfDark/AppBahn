package eu.appbahn.platform.common.security;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BiFunction<Jwt, List<String>, AuthContext> authContextResolver,
            Function<String, Optional<Authentication>> environmentTokenAuthenticator,
            @Qualifier("oidcHttpSecurityCustomizer") Optional<Customizer<HttpSecurity>> oidcCustomizer)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        .requestMatchers("/docs/api/**", "/scalar/**", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/api/v1/openapi/**")
                        .permitAll()
                        .requestMatchers("/api/v1/webhooks/**")
                        .permitAll()
                        // SPA and static assets
                        .requestMatchers("/", "/index.html", "/console/**", "/login", "/auth/complete")
                        .permitAll()
                        .requestMatchers("/api/v1/auth/**")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/config")
                        .permitAll()
                        .requestMatchers("/assets/**", "/favicon.ico", "/logo.png")
                        .permitAll()
                        // OAuth2 login endpoints handled by Spring
                        .requestMatchers("/oauth2/**", "/login/oauth2/**")
                        .permitAll()
                        // Internal API — only accessible by operator (client credentials with 'internal' scope)
                        .requestMatchers("/api/v1/internal/**")
                        .hasAuthority("SCOPE_internal")
                        // All other endpoints require authentication
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new AppBahnJwtAuthenticationConverter(authContextResolver))))
                .addFilterBefore(
                        new EnvironmentTokenFilter(environmentTokenAuthenticator),
                        BearerTokenAuthenticationFilter.class);

        // OAuth2 Login (OIDC authorization code + PKCE)
        oidcCustomizer.ifPresent(customizer -> {
            try {
                customizer.customize(http);
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure OAuth2 login", e);
            }
        });

        return http.build();
    }
}

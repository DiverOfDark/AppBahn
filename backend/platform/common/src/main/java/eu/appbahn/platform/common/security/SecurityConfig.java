package eu.appbahn.platform.common.security;

import eu.appbahn.platform.common.idempotency.IdempotencyFilter;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Dedicated filter chain for the operator tunnel paths. This chain runs before the
     * main OAuth2 chain (which would reject our operator-minted JWT because its signing
     * key isn't the platform's OIDC issuer) and delegates authentication entirely to
     * {@link eu.appbahn.platform.tunnel.auth.OperatorJwtVerifier} inside the controller.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain tunnelSecurityFilterChain(HttpSecurity http) throws Exception {
        var builder = PathPatternRequestMatcher.withDefaults();
        RequestMatcher tunnel = builder.matcher("/api/tunnel/v1/**");
        http.securityMatcher(tunnel)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BiFunction<Jwt, List<String>, AuthContext> authContextResolver,
            Function<String, Optional<Authentication>> environmentTokenAuthenticator,
            @Qualifier("oidcHttpSecurityCustomizer") Optional<Customizer<HttpSecurity>> oidcCustomizer,
            IdempotencyFilter idempotencyFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        .requestMatchers("/docs/api/**", "/scalar/**", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers(
                                "/api/v1/openapi",
                                "/api/v1/openapi.yaml",
                                "/api/v1/openapi/**",
                                "/api/v1/openapi.yaml/**",
                                "/v3/api-docs/**")
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
                        // Operator tunnel paths are served by a dedicated SecurityFilterChain
                        // (see tunnelSecurityFilterChain) that skips the OAuth2 resource-server
                        // filter. No rule needed here.
                        // All other endpoints require authentication
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new AppBahnJwtAuthenticationConverter(authContextResolver))))
                .addFilterBefore(
                        new EnvironmentTokenFilter(environmentTokenAuthenticator),
                        BearerTokenAuthenticationFilter.class)
                // After authorization (SecurityContext populated), before the controller — so the
                // filter can read the authenticated actor and short-circuit replays.
                .addFilterAfter(idempotencyFilter, AuthorizationFilter.class);

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

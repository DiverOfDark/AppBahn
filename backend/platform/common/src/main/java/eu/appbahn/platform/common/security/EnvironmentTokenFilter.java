package eu.appbahn.platform.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Function;
import java.util.Optional;

/**
 * Intercepts Bearer tokens with "abp_" prefix and authenticates them
 * as environment deploy tokens, bypassing JWT validation.
 */
public class EnvironmentTokenFilter extends OncePerRequestFilter {

    private static final String TOKEN_PREFIX = "abp_";
    private static final String BEARER_PREFIX = "Bearer ";

    private final Function<String, Optional<Authentication>> tokenAuthenticator;

    public EnvironmentTokenFilter(Function<String, Optional<Authentication>> tokenAuthenticator) {
        this.tokenAuthenticator = tokenAuthenticator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            if (token.startsWith(TOKEN_PREFIX)) {
                var auth = tokenAuthenticator.apply(token);
                if (auth.isPresent()) {
                    SecurityContextHolder.getContext().setAuthentication(auth.get());
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid or expired environment token\"}");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}

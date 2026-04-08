package eu.appbahn.platform.user.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Configures Spring's OAuth2 Login for the OIDC authorization code + PKCE flow.
 * <p>
 * Spring handles: PKCE generation, state management, authorization redirect,
 * callback, token exchange. We provide a custom success handler that extracts
 * the access token and redirects the SPA to pick it up.
 * <p>
 * Authorization requests are stored in base64-encoded JSON cookies (no server-side
 * session state), keeping the application stateless and horizontally scalable.
 * The cookie contains only public data (authorization URI, client ID, state,
 * scopes) — no secrets. Spring validates all fields server-side on callback.
 */
@Configuration
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.appbahn.client-id", matchIfMissing = false)
public class OidcLoginConfig {

    @Bean("oidcHttpSecurityCustomizer")
    public Customizer<HttpSecurity> oidcHttpSecurityCustomizer() {
        return http -> {
            try {
                http.oauth2Login(oauth2 -> oauth2.authorizationEndpoint(authz ->
                                authz.authorizationRequestRepository(new CookieAuthorizationRequestRepository()))
                        .successHandler((request, response, authentication) -> {
                            if (authentication instanceof OAuth2AuthenticationToken oauthToken
                                    && oauthToken.getPrincipal() instanceof OidcUser oidcUser) {
                                String accessToken = oidcUser.getIdToken().getTokenValue();
                                response.sendRedirect("/auth/complete?token=" + accessToken);
                            } else {
                                response.sendRedirect("/auth/complete");
                            }
                        }));
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure OAuth2 login", e);
            }
        };
    }

    /**
     * Stores OAuth2AuthorizationRequest in a short-lived HttpOnly cookie using
     * JSON + base64 encoding. No Java serialization (avoids RCE risk),
     * no encryption needed (all fields are public, already in browser URL).
     */
    static class CookieAuthorizationRequestRepository
            implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

        private static final String COOKIE_NAME = "oauth2_auth_request";
        private static final int COOKIE_MAX_AGE = 300;
        private static final ObjectMapper MAPPER = new ObjectMapper();

        @Override
        public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
            return getCookieValue(request);
        }

        @Override
        public void saveAuthorizationRequest(
                OAuth2AuthorizationRequest authorizationRequest,
                HttpServletRequest request,
                HttpServletResponse response) {
            if (authorizationRequest == null) {
                removeCookie(response);
                return;
            }
            var cookie = new Cookie(COOKIE_NAME, encode(serialize(authorizationRequest)));
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(COOKIE_MAX_AGE);
            cookie.setSecure(request.isSecure());
            response.addCookie(cookie);
        }

        @Override
        public OAuth2AuthorizationRequest removeAuthorizationRequest(
                HttpServletRequest request, HttpServletResponse response) {
            OAuth2AuthorizationRequest req = loadAuthorizationRequest(request);
            removeCookie(response);
            return req;
        }

        private OAuth2AuthorizationRequest getCookieValue(HttpServletRequest request) {
            if (request.getCookies() == null) return null;
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isEmpty()) {
                    return deserialize(decode(cookie.getValue()));
                }
            }
            return null;
        }

        private void removeCookie(HttpServletResponse response) {
            var cookie = new Cookie(COOKIE_NAME, "");
            cookie.setPath("/");
            cookie.setMaxAge(0);
            response.addCookie(cookie);
        }

        @SuppressWarnings("unchecked")
        private String serialize(OAuth2AuthorizationRequest req) {
            try {
                var map = new LinkedHashMap<String, Object>();
                map.put("authorizationUri", req.getAuthorizationUri());
                map.put("clientId", req.getClientId());
                map.put("redirectUri", req.getRedirectUri());
                map.put("state", req.getState());
                map.put("scopes", req.getScopes());
                map.put("authorizationRequestUri", req.getAuthorizationRequestUri());
                map.put("attributes", req.getAttributes());
                return MAPPER.writeValueAsString(map);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize authorization request", e);
            }
        }

        @SuppressWarnings("unchecked")
        private OAuth2AuthorizationRequest deserialize(String json) {
            if (json == null) return null;
            try {
                var map = MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
                var builder = OAuth2AuthorizationRequest.authorizationCode()
                        .authorizationUri((String) map.get("authorizationUri"))
                        .clientId((String) map.get("clientId"))
                        .redirectUri((String) map.get("redirectUri"))
                        .state((String) map.get("state"))
                        .authorizationRequestUri((String) map.get("authorizationRequestUri"));
                var scopes = map.get("scopes");
                if (scopes instanceof List<?> scopeList) {
                    builder.scopes(new LinkedHashSet<>((List<String>) scopeList));
                }
                var attributes = map.get("attributes");
                if (attributes instanceof Map<?, ?> attrMap) {
                    builder.attributes(attrs -> attrs.putAll((Map<String, Object>) attrMap));
                }
                return builder.build();
            } catch (Exception e) {
                return null;
            }
        }

        private static String encode(String value) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }

        private static String decode(String value) {
            try {
                return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return null;
            }
        }
    }
}

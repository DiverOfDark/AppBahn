package eu.appbahn.platform.user.controller;

import eu.appbahn.platform.api.AuthApi;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin controller that maps the generated API paths to Spring Security's
 * OAuth2 Login filter chain. The actual OIDC flow (redirect, PKCE, token
 * exchange) is handled by Spring's OAuth2AuthorizationRequestRedirectFilter
 * and OAuth2LoginAuthenticationFilter — see OidcLoginConfig.
 */
@RestController
@RequestMapping("/api/v1")
public class AuthController implements AuthApi {

    @Override
    public ResponseEntity<Void> authLogin() {
        // Redirect to Spring's OAuth2 authorization endpoint which handles PKCE, state, etc.
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/oauth2/authorization/appbahn"))
                .build();
    }

    @Override
    public ResponseEntity<Void> authCallback(String code, String state) {
        // This path is handled by Spring's OAuth2LoginAuthenticationFilter before
        // it reaches this controller. If we get here, something went wrong.
        return ResponseEntity.badRequest().build();
    }
}

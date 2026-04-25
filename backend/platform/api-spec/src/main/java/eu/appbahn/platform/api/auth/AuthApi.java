package eu.appbahn.platform.api.auth;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@Tag(name = "Auth")
public interface AuthApi {
    /**
     * GET /auth/callback : OIDC callback
     * Receives the authorization code from the OIDC provider, exchanges it for tokens server-side, then redirects the browser to /auth/complete?token&#x3D;{access_token} for the SPA to pick up.
     *
     * @param code  (optional)
     * @param state  (optional)
     * @return Success (status code 200)
     *         or Redirect to SPA with token (status code 302)
     *         or Bad request (status code 400)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/auth/callback",
            produces = {"application/json"})
    ResponseEntity<Object> authCallback(
            @Valid @RequestParam(value = "code", required = false) @Nullable String code,
            @Valid @RequestParam(value = "state", required = false) @Nullable String state);
    /**
     * GET /auth/login : Initiate OIDC login
     * Generates PKCE code verifier/challenge and state, then redirects the browser to the OIDC provider&#39;s authorization endpoint. No authentication required.
     *
     * @return Success (status code 200)
     *         or Redirect to OIDC authorization endpoint (status code 302)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/auth/login",
            produces = {"application/json"})
    ResponseEntity<Object> authLogin();
}

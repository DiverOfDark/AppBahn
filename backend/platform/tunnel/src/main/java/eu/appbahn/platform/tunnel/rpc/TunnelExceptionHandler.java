package eu.appbahn.platform.tunnel.rpc;

import eu.appbahn.platform.resource.service.ClusterOwnershipException;
import eu.appbahn.platform.tunnel.auth.OperatorJwtVerifier;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// HIGHEST_PRECEDENCE so this advice is consulted before GlobalExceptionHandler.
// Without it, Spring may pick GlobalExceptionHandler.handleGeneric(Exception) for
// TunnelApiException / TunnelAuthException / ClusterOwnershipException — the
// catch-all handler logs them as ERROR with a full stack trace, which makes
// every routine "cluster not approved" registration retry look like a bug.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = OperatorTunnelController.class)
public class TunnelExceptionHandler {

    @ExceptionHandler(TunnelApiException.class)
    public ResponseEntity<Map<String, String>> handle(TunnelApiException e) {
        return ResponseEntity.status(e.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", e.code(), "message", e.getMessage()));
    }

    @ExceptionHandler(OperatorJwtVerifier.TunnelAuthException.class)
    public ResponseEntity<Map<String, String>> handleAuth(OperatorJwtVerifier.TunnelAuthException e) {
        return ResponseEntity.status(401)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", "unauthenticated", "message", e.getMessage()));
    }

    @ExceptionHandler(ClusterOwnershipException.class)
    public ResponseEntity<Map<String, String>> handleOwnership(ClusterOwnershipException e) {
        return ResponseEntity.status(403)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", "permission_denied", "message", e.getMessage()));
    }
}

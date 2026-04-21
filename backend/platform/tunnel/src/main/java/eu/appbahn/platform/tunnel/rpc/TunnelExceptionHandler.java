package eu.appbahn.platform.tunnel.rpc;

import eu.appbahn.platform.tunnel.auth.OperatorJwtVerifier;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OperatorTunnelController.class)
public class TunnelExceptionHandler {

    @ExceptionHandler(TunnelConnectException.class)
    public ResponseEntity<Map<String, String>> handleConnect(TunnelConnectException e) {
        return ResponseEntity.status(e.httpStatus()).body(Map.of("code", e.code(), "message", e.getMessage()));
    }

    @ExceptionHandler(OperatorJwtVerifier.TunnelAuthException.class)
    public ResponseEntity<Map<String, String>> handleAuth(OperatorJwtVerifier.TunnelAuthException e) {
        return ResponseEntity.status(401).body(Map.of("code", "unauthenticated", "message", e.getMessage()));
    }
}

package eu.appbahn.operator.tunnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.shared.tunnel.OperatorJwt;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * Mints RS256 JWTs for the operator to include in every tunnel RPC. Token lifetime
 * comfortably exceeds expected clock drift — the jti replay cache + per-request
 * minting still provide replay resistance.
 */
@Service
public class OperatorJwtMinter {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final OperatorKeyManager keyManager;
    private final OperatorTunnelConfig config;
    private final ObjectMapper objectMapper;

    public OperatorJwtMinter(OperatorKeyManager keyManager, OperatorTunnelConfig config, ObjectMapper objectMapper) {
        this.keyManager = keyManager;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public String mint() {
        return OperatorJwt.newToken(
                        config.clusterName(), keyManager.operatorInstanceId().toString(), TTL)
                .mint(keyManager.privateKey(), objectMapper);
    }
}

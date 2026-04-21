package eu.appbahn.operator.tunnel;

import eu.appbahn.tunnel.v1.GoodbyeAck;
import eu.appbahn.tunnel.v1.GoodbyeRequest;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends a Goodbye RPC on graceful shutdown so the platform clears its
 * {@code cluster_session} row immediately rather than waiting for the heartbeat
 * to lapse after 30s. Best-effort: a network error here is logged and ignored.
 */
@Service
public class TunnelShutdownHook {

    private static final Logger log = LoggerFactory.getLogger(TunnelShutdownHook.class);

    private final OperatorTunnelClient tunnelClient;
    private final OperatorTunnelConfig config;

    public TunnelShutdownHook(OperatorTunnelClient tunnelClient, OperatorTunnelConfig config) {
        this.tunnelClient = tunnelClient;
        this.config = config;
    }

    @PreDestroy
    public void sayGoodbye() {
        try {
            GoodbyeRequest req = GoodbyeRequest.newBuilder()
                    .setClusterName(config.clusterName())
                    .setReason("graceful-shutdown")
                    .build();
            tunnelClient.unary("Goodbye", req, GoodbyeAck.newBuilder());
        } catch (Exception e) {
            log.debug("goodbye RPC failed on shutdown (non-fatal): {}", e.getMessage());
        }
    }
}

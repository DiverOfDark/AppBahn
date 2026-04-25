package eu.appbahn.operator.tunnel;

import eu.appbahn.operator.tunnel.client.model.GoodbyeRequest;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends a Goodbye call on graceful shutdown so the platform clears its
 * {@code cluster_session} row immediately rather than waiting for the heartbeat to lapse
 * after 30s. Best-effort: a network error here is logged and ignored.
 */
@Service
public class TunnelShutdownHook {

    private static final Logger log = LoggerFactory.getLogger(TunnelShutdownHook.class);

    private final TunnelApiClient tunnelApiClient;
    private final OperatorTunnelConfig config;

    public TunnelShutdownHook(TunnelApiClient tunnelApiClient, OperatorTunnelConfig config) {
        this.tunnelApiClient = tunnelApiClient;
        this.config = config;
    }

    @PreDestroy
    public void sayGoodbye() {
        try {
            var req = new GoodbyeRequest();
            req.setClusterName(config.clusterName());
            req.setReason("graceful-shutdown");
            tunnelApiClient.goodbye(req);
        } catch (Exception e) {
            log.debug("goodbye call failed on shutdown (non-fatal): {}", e.getMessage());
        }
    }
}

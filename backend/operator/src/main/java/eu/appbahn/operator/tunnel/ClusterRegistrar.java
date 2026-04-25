package eu.appbahn.operator.tunnel;

import eu.appbahn.operator.tunnel.client.model.RegisterClusterAck;
import eu.appbahn.operator.tunnel.client.model.RegisterClusterRequest;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Calls {@link TunnelApiClient#registerCluster} at startup and retries with bounded exponential
 * backoff until the platform accepts. Runs on a virtual thread so Spring context init isn't
 * blocked by a slow or unreachable platform.
 */
@Service
public class ClusterRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ClusterRegistrar.class);
    private static final long BACKOFF_INITIAL_MS = 1_000;
    private static final long BACKOFF_CAP_MS = 30_000;

    private final OperatorKeyManager keyManager;
    private final OperatorTunnelConfig config;
    private final TunnelApiClient tunnelApiClient;

    public ClusterRegistrar(
            OperatorKeyManager keyManager, OperatorTunnelConfig config, TunnelApiClient tunnelApiClient) {
        this.keyManager = keyManager;
        this.config = config;
        this.tunnelApiClient = tunnelApiClient;
    }

    @PostConstruct
    public void registerOnStartup() {
        if (config.platformBaseUrl() == null || config.platformBaseUrl().isBlank()) {
            throw new IllegalStateException("operator.tunnel.platform-base-url is required; "
                    + "the operator cannot register without knowing where the platform is");
        }
        Thread.ofVirtual().name("cluster-registrar").start(this::retryUntilRegistered);
    }

    private void retryUntilRegistered() {
        long backoffMs = BACKOFF_INITIAL_MS;
        while (!Thread.currentThread().isInterrupted()) {
            if (attemptRegistration()) {
                return;
            }
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            backoffMs = Math.min(backoffMs * 2, BACKOFF_CAP_MS);
        }
    }

    private boolean attemptRegistration() {
        try {
            var request = new RegisterClusterRequest();
            request.setClusterName(config.clusterName());
            request.setPublicKey(keyManager.publicKeyPem());
            request.setOperatorVersion(System.getProperty("appbahn.version", "dev"));
            request.setOperatorInstanceId(keyManager.operatorInstanceId().toString());
            RegisterClusterAck ack = tunnelApiClient.registerCluster(request);
            log.info(
                    "Cluster registration succeeded: cluster={} status={} fingerprint={}",
                    ack.getClusterName(),
                    ack.getStatus(),
                    ack.getPublicKeyFingerprint());
            return true;
        } catch (Exception e) {
            log.info("Cluster registration attempt failed; retrying: {}", e.getMessage());
            return false;
        }
    }
}

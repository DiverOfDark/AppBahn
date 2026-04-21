package eu.appbahn.operator.tunnel;

import eu.appbahn.tunnel.v1.SubscribeCommandsRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Holds one {@code SubscribeCommands} stream per operator process: a virtual thread opens it,
 * dispatches each {@link eu.appbahn.tunnel.v1.PlatformMessage} to {@link PlatformCommandHandler},
 * and reconnects with bounded exponential backoff on transport failure.
 */
@Service
public class SubscribeCommandsBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SubscribeCommandsBootstrap.class);

    private final OperatorTunnelClient tunnelClient;
    private final OperatorTunnelConfig config;
    private final OperatorKeyManager keyManager;
    private final PlatformCommandHandler commandHandler;
    private final AdmissionSnapshotCache admissionCache;
    private final AdminConfigCache adminConfigCache;
    private final long backoffInitialMs;
    private final long backoffCapMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread subscribeThread;

    public SubscribeCommandsBootstrap(
            OperatorTunnelClient tunnelClient,
            OperatorTunnelConfig config,
            OperatorKeyManager keyManager,
            PlatformCommandHandler commandHandler,
            AdmissionSnapshotCache admissionCache,
            AdminConfigCache adminConfigCache,
            @Value("${operator.tunnel.subscribe-backoff-initial-ms:1000}") long backoffInitialMs,
            @Value("${operator.tunnel.subscribe-backoff-cap-ms:30000}") long backoffCapMs) {
        this.tunnelClient = tunnelClient;
        this.config = config;
        this.keyManager = keyManager;
        this.commandHandler = commandHandler;
        this.admissionCache = admissionCache;
        this.adminConfigCache = adminConfigCache;
        this.backoffInitialMs = backoffInitialMs;
        this.backoffCapMs = backoffCapMs;
    }

    @PostConstruct
    public void start() {
        running.set(true);
        subscribeThread = Thread.ofVirtual().name("subscribe-commands").start(this::loop);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (subscribeThread != null) {
            subscribeThread.interrupt();
        }
    }

    private void loop() {
        long backoffMs = backoffInitialMs;
        while (running.get()) {
            try {
                // Echo the operator's currently-applied snapshot revisions so the platform
                // can skip a redundant HelloAck snapshot body when nothing has changed since
                // the previous connection.
                var req = SubscribeCommandsRequest.newBuilder()
                        .setClusterName(config.clusterName())
                        .setOperatorInstanceId(keyManager.operatorInstanceId().toString())
                        .setOperatorVersion(System.getProperty("appbahn.version", "dev"))
                        .setLastQuotaRbacRevision(admissionCache.revision())
                        .setLastAdminConfigRevision(adminConfigCache.revision())
                        .build();
                tunnelClient.subscribe(req, commandHandler::handle);
                backoffMs = backoffInitialMs;
            } catch (Exception e) {
                if (!running.get()) {
                    return;
                }
                log.info("SubscribeCommands stream ended: {} — reconnecting in {}ms", e.getMessage(), backoffMs);
            }
            try {
                TimeUnit.MILLISECONDS.sleep(backoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            backoffMs = Math.min(backoffMs * 2, backoffCapMs);
        }
    }
}

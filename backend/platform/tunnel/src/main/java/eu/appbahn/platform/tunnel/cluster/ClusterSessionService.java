package eu.appbahn.platform.tunnel.cluster;

import eu.appbahn.platform.tunnel.rpc.TunnelApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session ownership writes for {@code SubscribeCommands}. Lives in its own bean so the
 * {@link Transactional} boundary isn't bypassed by self-invocation from the controller.
 *
 * <p>Liveness lives on {@code cluster.last_heartbeat_at} only — session existence plus
 * sessionId equality is enough to track ownership, and a zombie session row owned by a
 * crashed replica is detected via the cluster timestamp going stale (only the session
 * holder bumps it).
 */
@Service
public class ClusterSessionService {

    private final ClusterRepository clusters;
    private final ClusterSessionRepository sessions;

    public ClusterSessionService(ClusterRepository clusters, ClusterSessionRepository sessions) {
        this.clusters = clusters;
        this.sessions = sessions;
    }

    @Transactional
    public void claimSession(String clusterName, UUID sessionId, String replicaId, String operatorInstanceId) {
        ClusterSessionEntity existing = sessions.findById(clusterName).orElse(null);
        if (existing == null) {
            existing = new ClusterSessionEntity();
            existing.setClusterName(clusterName);
            existing.setConnectedAt(Instant.now());
        }
        existing.setSubscribingReplicaId(replicaId);
        existing.setSessionId(sessionId);
        if (operatorInstanceId == null || operatorInstanceId.isBlank()) {
            throw TunnelApiException.invalidArgument("operatorInstanceId query parameter is required");
        }
        try {
            existing.setOperatorInstanceId(UUID.fromString(operatorInstanceId));
        } catch (IllegalArgumentException e) {
            throw TunnelApiException.invalidArgument("operatorInstanceId is not a valid UUID");
        }
        sessions.save(existing);

        clusters.findById(clusterName).ifPresent(cluster -> {
            cluster.setConnectedReplicaId(replicaId);
            cluster.setLastHeartbeatAt(Instant.now());
            clusters.save(cluster);
        });
    }

    @Transactional
    public void touchSession(String clusterName) {
        clusters.findById(clusterName).ifPresent(cluster -> {
            cluster.setLastHeartbeatAt(Instant.now());
            clusters.save(cluster);
        });
    }
}

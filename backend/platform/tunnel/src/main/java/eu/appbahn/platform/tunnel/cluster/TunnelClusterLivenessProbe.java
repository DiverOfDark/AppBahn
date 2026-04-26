package eu.appbahn.platform.tunnel.cluster;

import eu.appbahn.platform.common.exception.ConflictException;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.workspace.service.ClusterLivenessProbe;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TunnelClusterLivenessProbe implements ClusterLivenessProbe {

    static final String ERROR_CODE_CLUSTER_UNREACHABLE = "cluster_unreachable";
    static final Duration HEARTBEAT_FRESHNESS_THRESHOLD =
            Duration.ofSeconds(ClusterLivenessProbe.HEARTBEAT_FRESHNESS_THRESHOLD_SECONDS);

    private final ClusterRepository clusterRepository;

    public TunnelClusterLivenessProbe(ClusterRepository clusterRepository) {
        this.clusterRepository = clusterRepository;
    }

    @Override
    public void requireReachable(String clusterName) {
        ClusterEntity cluster = clusterRepository
                .findById(clusterName)
                .orElseThrow(() -> new NotFoundException("Target cluster not found: " + clusterName));

        if (cluster.getStatus() != ClusterStatus.APPROVED) {
            throw new ConflictException(
                    ERROR_CODE_CLUSTER_UNREACHABLE,
                    "Target cluster '" + clusterName + "' is not approved (status: " + cluster.getStatus() + ")",
                    List.of());
        }

        Instant lastHeartbeat = cluster.getLastHeartbeatAt();
        Instant now = Instant.now();
        if (lastHeartbeat == null
                || Duration.between(lastHeartbeat, now).compareTo(HEARTBEAT_FRESHNESS_THRESHOLD) > 0) {
            String message = lastHeartbeat == null
                    ? "Target cluster '" + clusterName + "' has never sent a heartbeat"
                    : "Target cluster '" + clusterName + "' has not heartbeat in "
                            + Math.max(0, Duration.between(lastHeartbeat, now).toSeconds()) + " seconds";
            throw new ConflictException(ERROR_CODE_CLUSTER_UNREACHABLE, message, List.of());
        }
    }
}

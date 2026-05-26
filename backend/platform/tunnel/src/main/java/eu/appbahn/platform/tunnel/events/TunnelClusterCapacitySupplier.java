package eu.appbahn.platform.tunnel.events;

import eu.appbahn.platform.api.ClusterCapacity;
import eu.appbahn.platform.api.tunnel.ClusterCapacityResult;
import eu.appbahn.platform.api.tunnel.QueryClusterCapacity;
import eu.appbahn.platform.tunnel.command.CommandResponseAwaiter;
import eu.appbahn.platform.tunnel.command.CommandTypes;
import eu.appbahn.platform.workspace.service.ClusterCapacitySupplier;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * Tunnel-backed {@link ClusterCapacitySupplier}: enqueues a {@link QueryClusterCapacity}
 * command, blocks for the operator's ack, and maps the {@link ClusterCapacityResult}
 * payload onto the public {@link ClusterCapacity} shape.
 */
@Service
public class TunnelClusterCapacitySupplier implements ClusterCapacitySupplier {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final CommandResponseAwaiter awaiter;

    public TunnelClusterCapacitySupplier(CommandResponseAwaiter awaiter) {
        this.awaiter = awaiter;
    }

    @Override
    public ClusterCapacity fetch(String clusterName) {
        ClusterCapacityResult result = awaiter.enqueueAndAwait(
                clusterName,
                CommandTypes.QUERY_CLUSTER_CAPACITY,
                new QueryClusterCapacity(),
                ClusterCapacityResult.class,
                TIMEOUT);

        var out = new ClusterCapacity();
        out.setClusterName(clusterName);
        out.setCpuAvailableMillicores(result.getCpuAvailableMillicores());
        out.setCpuTotalMillicores(result.getCpuTotalMillicores());
        out.setMemoryAvailableBytes(result.getMemoryAvailableBytes());
        out.setMemoryTotalBytes(result.getMemoryTotalBytes());
        out.setSchedulableNodes(result.getSchedulableNodes());
        return out;
    }
}

package eu.appbahn.platform.tunnel.cluster;

import eu.appbahn.platform.workspace.service.NodePoolCatalogue;
import eu.appbahn.shared.crd.NodePool;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TunnelNodePoolCatalogue implements NodePoolCatalogue {

    private final ClusterRepository clusters;

    public TunnelNodePoolCatalogue(ClusterRepository clusters) {
        this.clusters = clusters;
    }

    @Override
    public Set<String> nodePoolNames(String clusterName) {
        return nodePools(clusterName).stream()
                .map(NodePool::getName)
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.toSet());
    }

    @Override
    public List<NodePool> nodePools(String clusterName) {
        return clusters.findById(clusterName)
                .map(ClusterEntity::getConfig)
                .map(c -> c.getNodePools() == null ? List.<NodePool>of() : c.getNodePools())
                .orElseGet(List::of);
    }
}

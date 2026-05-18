package eu.appbahn.platform.api.tunnel;

import eu.appbahn.shared.crd.NodePool;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class AdminConfigSnapshot {

    @Nullable
    private String baseDomain;

    @Nullable
    private String registryUrl;

    @Nullable
    private String registryRepositoryPrefix;

    @Nullable
    private String namespacePrefix;

    /**
     * Per-cluster node-pool catalogue, sourced from the receiving cluster's {@code ClusterConfig}.
     * Operator looks up the selector + tolerations to stamp on pods when a resource pins a
     * {@code nodePool}; SPA uses it to populate the node-pool picker.
     */
    @Nullable
    private List<NodePool> nodePools;
}

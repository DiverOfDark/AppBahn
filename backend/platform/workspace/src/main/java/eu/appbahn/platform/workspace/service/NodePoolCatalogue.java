package eu.appbahn.platform.workspace.service;

import eu.appbahn.shared.crd.NodePool;
import java.util.List;
import java.util.Set;

/**
 * Resolves the {@code nodePool} catalogue declared on a registered cluster. Implemented by the
 * tunnel module (which owns the {@code cluster} table). {@link ResourceService} uses this on
 * create/update to reject a resource pinned to a nodePool the target cluster doesn't expose;
 * the SPA reads the full pool list via {@code GET /environments/{slug}/node-pools} to render the
 * Placement &amp; rollout picker on the create-resource page.
 */
public interface NodePoolCatalogue {

    /**
     * Names of the node pools declared on the cluster. Empty when the cluster has no
     * {@code ClusterConfig.nodePools} set — in that case any {@code nodePool} pin must be
     * rejected.
     */
    Set<String> nodePoolNames(String clusterName);

    /**
     * Full node-pool definitions for the cluster (name + displayName + selector + tolerations).
     * Empty list when the cluster is unknown or has no pools configured.
     */
    List<NodePool> nodePools(String clusterName);
}

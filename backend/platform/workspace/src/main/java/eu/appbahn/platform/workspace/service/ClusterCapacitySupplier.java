package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.api.ClusterCapacity;

/**
 * Queries the operator for live cluster CPU + memory headroom. Implemented in the tunnel
 * module — kept here as an SPI so the {@code /clusters/{slug}/capacity} controller can
 * sit in any module without a direct tunnel dependency.
 */
public interface ClusterCapacitySupplier {

    /**
     * @param clusterName the {@code environment.target_cluster} value
     * @return aggregate CPU/memory totals + headroom from the cluster
     */
    ClusterCapacity fetch(String clusterName);
}

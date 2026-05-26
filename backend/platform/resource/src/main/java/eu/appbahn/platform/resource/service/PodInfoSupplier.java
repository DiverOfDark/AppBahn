package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.resource.PodsResponse;

/**
 * Queries the operator for the pod list backing a Resource. Implemented in the tunnel
 * module — kept here as an SPI so the resource controller stays free of tunnel imports.
 */
public interface PodInfoSupplier {

    /**
     * @param clusterName  destination cluster
     * @param namespace    pod namespace (derived from environment slug)
     * @param resourceSlug Resource slug; used to filter pods by the
     *                     {@code appbahn.eu/resource} label
     * @return the per-pod snapshot from the operator
     */
    PodsResponse fetch(String clusterName, String namespace, String resourceSlug);
}

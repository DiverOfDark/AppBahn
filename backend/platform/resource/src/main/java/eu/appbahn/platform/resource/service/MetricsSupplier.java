package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.resource.MetricsResponse;
import eu.appbahn.platform.api.tunnel.MetricKind;

/**
 * Queries the operator for time-series metrics backing a Resource. Implemented in the tunnel
 * module — kept here as an SPI so the resource controller/service stay free of tunnel imports
 * (mirrors {@link PodInfoSupplier}).
 */
public interface MetricsSupplier {

    /**
     * @param clusterName  destination cluster
     * @param namespace    pod namespace (derived from environment slug)
     * @param resourceSlug Resource slug; used to filter pods by the {@code appbahn.eu/resource} label
     * @param kind         which time-series to query
     * @param startEpochSeconds range start (Unix epoch seconds)
     * @param endEpochSeconds   range end (Unix epoch seconds)
     * @param stepSeconds       resolution in seconds
     * @param pod          optional pod-name filter (null = all pods)
     * @return per-pod series; {@link MetricsResponse#getMessage()} is set on graceful degradation
     */
    MetricsResponse fetch(
            String clusterName,
            String namespace,
            String resourceSlug,
            MetricKind kind,
            long startEpochSeconds,
            long endEpochSeconds,
            int stepSeconds,
            String pod);
}

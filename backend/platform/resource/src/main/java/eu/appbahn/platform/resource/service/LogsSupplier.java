package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.resource.LogResponse;

/**
 * Queries the operator for log lines backing a Resource. Implemented in the tunnel module — kept
 * here as an SPI so the resource controller/service stay free of tunnel imports (mirrors
 * {@link MetricsSupplier}).
 */
public interface LogsSupplier {

    /**
     * @param clusterName       destination cluster
     * @param namespace         pod namespace (derived from environment slug)
     * @param resourceSlug      Resource slug; used to filter pods by the {@code appbahn.eu/resource} label
     * @param pod               optional pod-name filter (null = all pods)
     * @param container         optional container-name filter (null = all containers)
     * @param deploymentId      optional deployment-id filter (null = all deployments)
     * @param sinceEpochSeconds lower time bound (Unix epoch seconds); 0 = no lower bound
     * @param limit             maximum number of lines to return
     * @return matched lines; {@link LogResponse#getMessage()} is set on graceful degradation
     */
    LogResponse fetch(
            String clusterName,
            String namespace,
            String resourceSlug,
            String pod,
            String container,
            String deploymentId,
            long sinceEpochSeconds,
            int limit);
}

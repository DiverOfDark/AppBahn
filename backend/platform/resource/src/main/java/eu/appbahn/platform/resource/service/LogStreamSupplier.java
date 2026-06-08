package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.resource.LogStreamLogFrame;
import java.util.List;

/**
 * Tails new container log lines for a Resource. Implemented in the tunnel module — kept here as an
 * SPI so the resource service stays free of tunnel imports (mirrors {@link LogsSupplier}).
 *
 * <p>The streaming service calls {@link #tail} repeatedly with the last-seen line timestamp as the
 * lower bound, advancing the cursor each round. Each call is a bounded one-shot query against the
 * log provider through the tunnel — there is no long-lived operator-side cursor.
 */
public interface LogStreamSupplier {

    /**
     * @param clusterName       destination cluster
     * @param namespace         pod namespace (derived from environment slug)
     * @param resourceSlug      Resource slug; filters pods by the {@code appbahn.eu/resource} label
     * @param pod               optional pod-name filter (null = all pods)
     * @param container         optional container-name filter (null = all containers)
     * @param sinceEpochSeconds lower time bound (Unix epoch seconds); only lines strictly newer
     *                          than this are returned
     * @param limit             maximum number of lines to return this round
     * @return matched lines, oldest first; empty when none are newer than the bound or no provider
     *         is configured
     */
    List<LogStreamLogFrame> tail(
            String clusterName,
            String namespace,
            String resourceSlug,
            String pod,
            String container,
            long sinceEpochSeconds,
            int limit);
}

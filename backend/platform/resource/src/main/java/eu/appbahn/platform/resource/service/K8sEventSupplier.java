package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.resource.LogStreamEventFrame;
import java.time.Instant;
import java.util.List;

/**
 * Drains buffered Kubernetes events for a Resource. Implemented in the tunnel module — kept here as
 * an SPI so the resource service stays free of tunnel imports.
 *
 * <p>The operator forwards core/v1 Events for objects owned by a Resource over the tunnel; the
 * tunnel-side implementation keeps a short per-Resource ring buffer of recent events. The streaming
 * service polls {@link #drainSince} to forward new events onto the SSE stream as {@code k8s_event}
 * frames.
 */
public interface K8sEventSupplier {

    /**
     * @param clusterName  cluster the Resource lives on
     * @param namespace    Resource namespace
     * @param resourceSlug Resource slug
     * @param after        only events whose buffered arrival time is strictly after this are
     *                     returned; pass {@link Instant#EPOCH} for the first call
     * @return matched events ordered by buffered arrival time, oldest first
     */
    List<LogStreamEventFrame> drainSince(String clusterName, String namespace, String resourceSlug, Instant after);

    /** Buffered arrival time of the newest event for the Resource, or {@code after} when none. */
    Instant latestArrival(String clusterName, String namespace, String resourceSlug, Instant after);
}

package eu.appbahn.platform.workspace.service;

/**
 * Synchronously asserts that a target cluster is reachable before enqueuing work.
 * Implemented by the tunnel module, which owns the {@code cluster} table populated
 * via operator registration and heartbeats.
 *
 * <p>"Reachable" means the cluster is approved AND its last heartbeat is recent
 * (within {@link #HEARTBEAT_FRESHNESS_THRESHOLD_SECONDS}). Either condition failing
 * surfaces as a 409 to the caller, instead of letting the command sit unacked for
 * the full pending-command timeout.
 */
public interface ClusterLivenessProbe {

    /**
     * Heartbeat freshness window, in seconds. The operator bumps {@code last_heartbeat_at}
     * every few seconds while its SSE stream is open; 30s is large enough to absorb GC
     * pauses and reconnect blips, small enough that the API's 409 fires well before the
     * 5-minute pending-command sweeper.
     */
    long HEARTBEAT_FRESHNESS_THRESHOLD_SECONDS = 30;

    /**
     * Verify that the named cluster is approved and recently heartbeat.
     *
     * @param clusterName the {@code environment.target_cluster} value
     * @throws eu.appbahn.platform.common.exception.ConflictException with error code
     *     {@code cluster_unreachable} when the cluster is not approved or the last
     *     heartbeat is older than {@link #HEARTBEAT_FRESHNESS_THRESHOLD_SECONDS} seconds
     * @throws eu.appbahn.platform.common.exception.NotFoundException when no cluster with
     *     that name exists (data integrity issue — the FK should prevent this)
     */
    void requireReachable(String clusterName);
}

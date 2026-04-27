package eu.appbahn.platform.resource.service;

/**
 * Thrown by {@link ResourceSyncService} when an operator-pushed sync payload references
 * an environment that does not belong to the JWT-verified cluster. Maps to HTTP 403
 * {@code permission_denied} in the tunnel exception handler. The whole payload is
 * rejected (no rows are applied) — easier to reason about than partial application.
 */
public class ClusterOwnershipException extends RuntimeException {

    public ClusterOwnershipException(String message) {
        super(message);
    }
}

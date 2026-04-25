package eu.appbahn.operator.tunnel;

/**
 * Wire {@code event:} names for SSE frames the operator receives on the tunnel's
 * {@code /commands} stream. The platform side declares these constants on its DTO classes;
 * we re-declare them here so the operator stops depending on {@code :platform:api-spec}.
 * Drift between server and client values is exercised by the e2e suite — first frame mismatch
 * fails the {@code HelloAck} parse on subscribe.
 */
final class TunnelEventNames {

    private TunnelEventNames() {}

    static final String HELLO_ACK = "hello-ack";
    static final String ADMIN_CONFIG_PUSH = "admin-config-push";
    static final String QUOTA_RBAC_CACHE_PUSH = "quota-rbac-cache-push";
    static final String APPLY_RESOURCE = "apply-resource";
    static final String DELETE_RESOURCE = "delete-resource";
    static final String APPLY_NAMESPACE = "apply-namespace";
    static final String DELETE_NAMESPACE = "delete-namespace";
    static final String KEEPALIVE = "keepalive";
}

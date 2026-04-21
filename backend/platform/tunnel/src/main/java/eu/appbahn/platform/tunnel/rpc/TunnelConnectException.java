package eu.appbahn.platform.tunnel.rpc;

/**
 * Thrown from tunnel RPC handlers when a request is malformed or unauthorized.
 * The corresponding HTTP status + Connect error JSON is rendered by
 * {@link TunnelExceptionHandler}.
 */
public class TunnelConnectException extends RuntimeException {

    private final int httpStatus;
    private final String code;

    public TunnelConnectException(int httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public static TunnelConnectException unauthenticated(String message) {
        return new TunnelConnectException(401, "unauthenticated", message);
    }

    public static TunnelConnectException permissionDenied(String message) {
        return new TunnelConnectException(403, "permission_denied", message);
    }

    public static TunnelConnectException invalidArgument(String message) {
        return new TunnelConnectException(400, "invalid_argument", message);
    }

    public static TunnelConnectException notFound(String message) {
        return new TunnelConnectException(404, "not_found", message);
    }
}

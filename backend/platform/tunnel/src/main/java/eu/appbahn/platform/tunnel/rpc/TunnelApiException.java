package eu.appbahn.platform.tunnel.rpc;

/**
 * Thrown from tunnel endpoint handlers when a request is malformed or unauthorized.
 * {@link TunnelExceptionHandler} renders it as a JSON error body with the matching HTTP status.
 */
public class TunnelApiException extends RuntimeException {

    private final int httpStatus;
    private final String code;

    public TunnelApiException(int httpStatus, String code, String message) {
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

    public static TunnelApiException unauthenticated(String message) {
        return new TunnelApiException(401, "unauthenticated", message);
    }

    public static TunnelApiException permissionDenied(String message) {
        return new TunnelApiException(403, "permission_denied", message);
    }

    public static TunnelApiException invalidArgument(String message) {
        return new TunnelApiException(400, "invalid_argument", message);
    }

    public static TunnelApiException notFound(String message) {
        return new TunnelApiException(404, "not_found", message);
    }
}

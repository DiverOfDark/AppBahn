package eu.appbahn.platform.common.exception;

/**
 * Marker exception for endpoints whose REST contract is published but whose
 * implementation has not landed yet. {@link GlobalExceptionHandler} maps it to
 * HTTP 501 with the canonical {@code {"error":"not_implemented","message":...}}
 * body.
 */
public class NotImplementedException extends RuntimeException {

    public static final String ERROR_CODE = "not_implemented";
    public static final String DEFAULT_MESSAGE = "This endpoint is not yet available";

    public NotImplementedException() {
        super(DEFAULT_MESSAGE);
    }
}

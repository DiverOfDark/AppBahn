package eu.appbahn.platform.common.exception;

import java.util.List;

public class ConflictException extends RuntimeException {

    private final String errorCode;
    private final List<String> details;

    public ConflictException(String message) {
        super(message);
        this.errorCode = "conflict";
        this.details = List.of();
    }

    public ConflictException(String message, List<String> details) {
        super(message);
        this.errorCode = "conflict";
        this.details = details;
    }

    public ConflictException(String errorCode, String message, List<String> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public List<String> getDetails() {
        return details;
    }
}

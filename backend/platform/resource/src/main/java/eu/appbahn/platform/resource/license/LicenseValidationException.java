package eu.appbahn.platform.resource.license;

/**
 * Thrown at platform startup when a configured license file fails to load, parse, verify, or is
 * expired. Always fatal — Spring lets it propagate, the boot fails, and operators see the cause
 * in the log instead of the platform silently degrading to community mode.
 */
public class LicenseValidationException extends RuntimeException {

    public LicenseValidationException(String message) {
        super(message);
    }

    public LicenseValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

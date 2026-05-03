package eu.appbahn.platform.resource.license;

import java.time.Instant;
import java.util.UUID;

/**
 * Verified license claims extracted from a signed JWS file. Immutable. {@code null} when the
 * platform is running in community mode (no licence configured).
 */
public record LicenseClaims(String customerId, UUID licenseId, int maxResources, Instant issuedAt, Instant expiresAt) {

    /** Issuer claim every AppBahn license carries. Verified at load time. */
    public static final String ISSUER = "appbahn";
}

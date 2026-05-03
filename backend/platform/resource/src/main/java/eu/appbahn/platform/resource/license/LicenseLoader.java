package eu.appbahn.platform.resource.license;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Loads and verifies the platform's license file at startup. Pure logic — no Spring annotations
 * here so unit tests can swap the public-key {@link Resource} freely. Wiring into Spring lives in
 * {@code LicenseConfiguration}.
 */
public class LicenseLoader {

    /** JOSE header `typ` every AppBahn license carries. */
    public static final String LICENSE_TYP = "license+jws";

    private static final String CLAIM_MAX_RESOURCES = "maxResources";
    private static final String CLAIM_LICENSE_ID = "licenseId";

    private final Resource publicKeyResource;

    public LicenseLoader(Resource publicKeyResource) {
        this.publicKeyResource = publicKeyResource;
    }

    /** Default loader wired into Spring: reads the public key from the platform JAR. */
    public static LicenseLoader defaultLoader() {
        return new LicenseLoader(new ClassPathResource("license/public-key.pem"));
    }

    /**
     * Load + verify a license from disk. Throws {@link LicenseValidationException} for any
     * recoverable input problem (missing file, malformed JWS, bad signature, expired, missing
     * required claim). The platform treats every failure as fatal at startup.
     */
    public LicenseClaims load(Path licenseFile) {
        String compact;
        try {
            compact = Files.readString(licenseFile, StandardCharsets.UTF_8).trim();
        } catch (NoSuchFileException e) {
            throw new LicenseValidationException("License file not found: " + licenseFile, e);
        } catch (IOException e) {
            throw new LicenseValidationException("Failed to read license file: " + licenseFile, e);
        }
        return verify(compact);
    }

    /** Verify a compact JWS string (visible for testing). */
    public LicenseClaims verify(String compactJws) {
        SignedJWT jws;
        try {
            jws = SignedJWT.parse(compactJws);
        } catch (ParseException e) {
            throw new LicenseValidationException("License is not a valid compact JWS", e);
        }

        if (!JWSAlgorithm.EdDSA.equals(jws.getHeader().getAlgorithm())) {
            throw new LicenseValidationException(
                    "License header alg must be EdDSA, got " + jws.getHeader().getAlgorithm());
        }
        if (jws.getHeader().getType() == null
                || !LICENSE_TYP.equals(jws.getHeader().getType().getType())) {
            throw new LicenseValidationException("License header typ must be " + LICENSE_TYP + ", got "
                    + jws.getHeader().getType());
        }

        OctetKeyPair publicKey = loadPublicKey();
        try {
            if (!jws.verify(new Ed25519Verifier(publicKey))) {
                throw new LicenseValidationException("License signature is invalid");
            }
        } catch (JOSEException e) {
            throw new LicenseValidationException("Failed to verify license signature", e);
        }

        JWTClaimsSet claims;
        try {
            claims = jws.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new LicenseValidationException("License payload is not a valid JWT claims set", e);
        }

        if (!LicenseClaims.ISSUER.equals(claims.getIssuer())) {
            throw new LicenseValidationException(
                    "License iss must be " + LicenseClaims.ISSUER + ", got " + claims.getIssuer());
        }

        String customerId = claims.getSubject();
        if (customerId == null || customerId.isBlank()) {
            throw new LicenseValidationException("License is missing required `sub` claim");
        }

        Instant issuedAt =
                claims.getIssueTime() == null ? null : claims.getIssueTime().toInstant();
        if (issuedAt == null) {
            throw new LicenseValidationException("License is missing required `iat` claim");
        }

        Instant expiresAt = claims.getExpirationTime() == null
                ? null
                : claims.getExpirationTime().toInstant();
        if (expiresAt == null) {
            throw new LicenseValidationException("License is missing required `exp` claim");
        }

        if (Instant.now().isAfter(expiresAt)) {
            throw new LicenseValidationException("License expired on "
                    + DateTimeFormatter.ISO_INSTANT.format(expiresAt)
                    + "; contact owner@appbahn.cloud");
        }

        Integer maxResources;
        try {
            maxResources = claims.getIntegerClaim(CLAIM_MAX_RESOURCES);
        } catch (ParseException e) {
            throw new LicenseValidationException("License `maxResources` claim is not an integer", e);
        }
        if (maxResources == null) {
            throw new LicenseValidationException("License is missing required `maxResources` claim");
        }
        if (maxResources <= 0) {
            throw new LicenseValidationException("License `maxResources` must be positive, got " + maxResources);
        }

        UUID licenseId;
        try {
            String raw = claims.getStringClaim(CLAIM_LICENSE_ID);
            if (raw == null || raw.isBlank()) {
                throw new LicenseValidationException("License is missing required `licenseId` claim");
            }
            licenseId = UUID.fromString(raw);
        } catch (ParseException e) {
            throw new LicenseValidationException("License `licenseId` claim is malformed", e);
        } catch (IllegalArgumentException e) {
            throw new LicenseValidationException("License `licenseId` claim is not a valid UUID", e);
        }

        return new LicenseClaims(customerId, licenseId, maxResources, issuedAt, expiresAt);
    }

    private OctetKeyPair loadPublicKey() {
        try (InputStream in = publicKeyResource.getInputStream()) {
            return LicenseKeyCodec.toOctetKeyPair(LicenseKeyCodec.readPublicKeyPem(in));
        } catch (IOException e) {
            throw new LicenseValidationException("Failed to read license public key from " + publicKeyResource, e);
        }
    }
}

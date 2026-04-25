package eu.appbahn.shared.tunnel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Typed model for the operator's tunnel JWT. Owns both directions:
 * {@link #mint(PrivateKey, ObjectMapper)} on the operator side, {@link #parse(String, ObjectMapper)}
 * on the platform side. Always RS256, always two-segment (header + payload) signed input.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperatorJwt(
        @JsonProperty("iss") String clusterName,
        @JsonProperty("sub") String operatorInstanceId,
        @JsonProperty("jti") String jti,
        @JsonProperty("iat") long issuedAtEpochSeconds,
        @JsonProperty("exp") long expiresAtEpochSeconds) {

    private static final String ALG = "RS256";
    private static final String TYP = "JWT";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    public static OperatorJwt newToken(String clusterName, String operatorInstanceId, Duration ttl) {
        Instant now = Instant.now();
        return new OperatorJwt(
                clusterName,
                operatorInstanceId,
                UUID.randomUUID().toString(),
                now.getEpochSecond(),
                now.plus(ttl).getEpochSecond());
    }

    public Instant issuedAt() {
        return Instant.ofEpochSecond(issuedAtEpochSeconds);
    }

    public Instant expiresAt() {
        return Instant.ofEpochSecond(expiresAtEpochSeconds);
    }

    /** Serialize, sign, and emit the compact JWT representation. */
    public String mint(PrivateKey privateKey, ObjectMapper objectMapper) {
        try {
            String header = base64Url(objectMapper.writeValueAsBytes(Map.of("alg", ALG, "typ", TYP)));
            String payload = base64Url(objectMapper.writeValueAsBytes(this));
            String signingInput = header + "." + payload;
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initSign(privateKey);
            sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + base64Url(sig.sign());
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Could not mint operator JWT", e);
        }
    }

    /**
     * Split + decode without checking the signature. Caller looks up the issuer's public
     * key (which depends on {@link #clusterName()}) and then calls
     * {@link Signed#verifySignature(PublicKey)} to complete the check.
     */
    public static Signed parse(String token, ObjectMapper objectMapper) {
        if (token == null || token.isBlank()) {
            throw new MalformedJwtException("missing token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new MalformedJwtException("expected three dot-separated segments");
        }
        try {
            Map<?, ?> header = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
            String alg = String.valueOf(header.get("alg"));
            if (!ALG.equals(alg)) {
                throw new MalformedJwtException("unsupported alg: " + alg);
            }
            OperatorJwt claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), OperatorJwt.class);
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            String signedInput = parts[0] + "." + parts[1];
            return new Signed(claims, signedInput, signature);
        } catch (IOException | IllegalArgumentException e) {
            throw new MalformedJwtException("malformed JWT segments: " + e.getMessage());
        }
    }

    /** Result of {@link OperatorJwt#parse(String, ObjectMapper)}: the claims + the signed bytes. */
    public record Signed(OperatorJwt claims, String signedInput, byte[] signature) {

        public boolean verifySignature(PublicKey publicKey) {
            try {
                Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
                sig.initVerify(publicKey);
                sig.update(signedInput.getBytes(StandardCharsets.US_ASCII));
                return sig.verify(signature);
            } catch (GeneralSecurityException e) {
                return false;
            }
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Thrown by {@link #parse(String, ObjectMapper)} when the wire format is broken. */
    public static class MalformedJwtException extends RuntimeException {
        public MalformedJwtException(String message) {
            super(message);
        }
    }
}

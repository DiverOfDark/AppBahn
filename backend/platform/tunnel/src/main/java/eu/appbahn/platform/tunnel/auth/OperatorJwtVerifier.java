package eu.appbahn.platform.tunnel.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.tunnel.cluster.ClusterEntity;
import eu.appbahn.platform.tunnel.cluster.ClusterRepository;
import eu.appbahn.platform.tunnel.cluster.ClusterStatus;
import eu.appbahn.shared.tunnel.OperatorJwt;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Verifies operator JWTs against the stored {@link ClusterEntity#getPublicKey()}.
 * RS256 only; cross-replica replay protection is short-window + in-memory, which
 * suffices because JWTs expire in ≤60 seconds and each replica is fronted by the
 * same registration+approval state in Postgres.
 */
@Service
public class OperatorJwtVerifier {

    private static final Duration ALLOWED_CLOCK_SKEW = Duration.ofSeconds(30);

    private final ClusterRepository clusters;
    private final ObjectMapper objectMapper;

    // JTI cache: (iss, jti) -> expiry instant. A ConcurrentHashMap with lazy eviction is
    // fine given sub-minute JWT lifetimes and low QPS on the tunnel. Entries are pruned on
    // next access when their expiry passes.
    private final ConcurrentHashMap<String, Instant> seenJtis = new ConcurrentHashMap<>();

    public OperatorJwtVerifier(ClusterRepository clusters, ObjectMapper objectMapper) {
        this.clusters = clusters;
        this.objectMapper = objectMapper;
    }

    public OperatorJwt verify(String bearer) {
        OperatorJwt.Signed signed;
        try {
            signed = OperatorJwt.parse(bearer, objectMapper);
        } catch (OperatorJwt.MalformedJwtException e) {
            throw new TunnelAuthException(e.getMessage());
        }
        OperatorJwt claims = signed.claims();
        if (claims.clusterName() == null || claims.jti() == null || claims.expiresAtEpochSeconds() == 0L) {
            throw new TunnelAuthException("missing required claims (iss, jti, exp)");
        }

        Instant now = Instant.now();
        Instant expiry = claims.expiresAt();
        if (now.isAfter(expiry.plus(ALLOWED_CLOCK_SKEW))) {
            throw new TunnelAuthException("JWT expired");
        }
        if (claims.issuedAtEpochSeconds() > 0L && claims.issuedAt().isAfter(now.plus(ALLOWED_CLOCK_SKEW))) {
            throw new TunnelAuthException("JWT issued in the future");
        }

        ClusterEntity cluster = clusters.findById(claims.clusterName())
                .orElseThrow(() -> new TunnelAuthException("unknown cluster: " + claims.clusterName()));
        if (cluster.getStatus() != ClusterStatus.APPROVED) {
            throw new TunnelAuthException("cluster not approved: " + claims.clusterName());
        }
        if (cluster.getPublicKey() == null || cluster.getPublicKey().isBlank()) {
            throw new TunnelAuthException("cluster has no public key: " + claims.clusterName());
        }

        if (!signed.verifySignature(decodePublicKey(cluster.getPublicKey()))) {
            throw new TunnelAuthException("invalid signature");
        }

        String jtiKey = claims.clusterName() + "|" + claims.jti();
        Instant previous = seenJtis.putIfAbsent(jtiKey, expiry.plus(ALLOWED_CLOCK_SKEW));
        if (previous != null) {
            throw new TunnelAuthException("jti replayed");
        }
        // Opportunistic cleanup of stale jti entries.
        seenJtis.entrySet().removeIf(e -> e.getValue().isBefore(now));

        return claims;
    }

    private PublicKey decodePublicKey(String pem) {
        try {
            String stripped = pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                    .replaceAll("-----END [A-Z ]+-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(stripped);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new TunnelAuthException("could not decode cluster public key");
        }
    }

    public static class TunnelAuthException extends RuntimeException {
        public TunnelAuthException(String message) {
            super(message);
        }
    }
}

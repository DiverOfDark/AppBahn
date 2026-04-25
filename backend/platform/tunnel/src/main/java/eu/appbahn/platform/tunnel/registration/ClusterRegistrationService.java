package eu.appbahn.platform.tunnel.registration;

import eu.appbahn.platform.tunnel.cluster.ClusterEntity;
import eu.appbahn.platform.tunnel.cluster.ClusterRepository;
import eu.appbahn.platform.tunnel.cluster.ClusterStatus;
import eu.appbahn.platform.tunnel.rpc.TunnelApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClusterRegistrationService {

    private final ClusterRepository clusters;
    private final OperatorPubkeyReader pubkeyReader;

    public ClusterRegistrationService(ClusterRepository clusters, OperatorPubkeyReader pubkeyReader) {
        this.clusters = clusters;
        this.pubkeyReader = pubkeyReader;
    }

    @Transactional
    public ClusterRegistrationResponse register(ClusterRegistrationRequest request) {
        String fingerprint = fingerprint(request.publicKey());

        ClusterEntity cluster = clusters.findById(request.clusterName()).orElseGet(() -> {
            ClusterEntity fresh = new ClusterEntity();
            fresh.setName(request.clusterName());
            fresh.setStatus(ClusterStatus.PENDING);
            fresh.setCreatedAt(Instant.now());
            return fresh;
        });

        // Once a cluster has a public key on file, subsequent registrations must carry
        // the same key. Rotations are manual (admin deletes the cluster row; operator
        // re-registers with the fresh key). Without this gate an unauthenticated caller
        // could overwrite an APPROVED cluster's key and impersonate it.
        if (cluster.getPublicKey() != null
                && !cluster.getPublicKey().isBlank()
                && !keysEqual(cluster.getPublicKey(), request.publicKey())) {
            throw TunnelApiException.permissionDenied(
                    "cluster " + request.clusterName() + " already registered with a different public key");
        }

        cluster.setPublicKey(request.publicKey());
        cluster.setPublicKeyFingerprint(fingerprint);
        cluster.setOperatorVersion(request.operatorVersion());
        if (request.operatorInstanceId() != null) {
            try {
                cluster.setOperatorInstanceId(UUID.fromString(request.operatorInstanceId()));
            } catch (IllegalArgumentException e) {
                throw TunnelApiException.invalidArgument("operator_instance_id is not a valid UUID");
            }
        }

        // Auto-approval: platform reads its own namespace's ConfigMap and promotes only if
        // the advertised public key matches the one the operator published locally.
        if (cluster.getStatus() != ClusterStatus.APPROVED) {
            boolean matched = pubkeyReader
                    .readOperatorPublicKey()
                    .map(key -> keysEqual(key, request.publicKey()))
                    .orElse(false);
            if (matched) {
                cluster.setStatus(ClusterStatus.APPROVED);
                cluster.setApprovedAt(Instant.now());
            }
        }

        clusters.save(cluster);
        return new ClusterRegistrationResponse(cluster.getName(), cluster.getStatus(), fingerprint);
    }

    static String fingerprint(String publicKeyPem) {
        try {
            String stripped = publicKeyPem
                    .replaceAll("-----BEGIN [A-Z ]+-----", "")
                    .replaceAll("-----END [A-Z ]+-----", "")
                    .replaceAll("\\s", "");
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(stripped.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private boolean keysEqual(String a, String b) {
        return normalise(a).equals(normalise(b));
    }

    private String normalise(String pem) {
        return pem.replaceAll("\\s", "");
    }
}

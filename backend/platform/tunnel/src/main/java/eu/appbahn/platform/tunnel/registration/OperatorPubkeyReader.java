package eu.appbahn.platform.tunnel.registration;

import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Reads the operator's public key from the {@code appbahn-operator-key} Secret that the
 * operator manages in its own namespace (which is the same namespace as the platform in
 * single-cluster mode). A match between the Secret's {@code public-key} field and the
 * operator's registration request is what auto-promotes a new cluster from PENDING to
 * APPROVED.
 *
 * <p>The Kubernetes client is optional: integration tests run with
 * {@code platform.kubernetes.enabled=false} and expect the platform to function without
 * cluster access. In that mode this reader always returns empty, leaving registrations
 * in PENDING — which is the correct behaviour.
 */
@Component
public class OperatorPubkeyReader {

    private static final String SECRET_NAME = "appbahn-operator-key";
    private static final String PUBLIC_KEY_FIELD = "public-key";

    private final Optional<KubernetesClient> kubernetesClient;

    public OperatorPubkeyReader(Optional<KubernetesClient> kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    public Optional<String> readOperatorPublicKey() {
        if (kubernetesClient.isEmpty()) {
            return Optional.empty();
        }
        try {
            // In-cluster config resolves the running pod's namespace from the mounted SA token.
            var client = kubernetesClient.get();
            var secret = client.secrets()
                    .inNamespace(client.getNamespace())
                    .withName(SECRET_NAME)
                    .get();
            if (secret == null || secret.getData() == null) {
                return Optional.empty();
            }
            String base64Der = secret.getData().get(PUBLIC_KEY_FIELD);
            if (base64Der == null || base64Der.isBlank()) {
                return Optional.empty();
            }
            // Secret stores base64(DER); registration sends PEM. Re-wrap so the
            // ClusterRegistrationService whitespace-stripped comparison matches.
            byte[] der = Base64.getDecoder().decode(base64Der);
            return Optional.of(pemEncode(der));
        } catch (Exception e) {
            // Any K8s client failure is treated as "no key found" so registration stays PENDING.
            return Optional.empty();
        }
    }

    private static String pemEncode(byte[] der) {
        return "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(der) + "\n-----END PUBLIC KEY-----";
    }
}

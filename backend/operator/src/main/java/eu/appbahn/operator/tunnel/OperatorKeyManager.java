package eu.appbahn.operator.tunnel;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages the operator's RSA-2048 keypair. On first start the keypair is generated
 * and persisted as a Kubernetes Secret ({@code appbahn-operator-key}) in the
 * operator's own namespace. The platform reads the {@code public-key} field from the
 * same Secret to auto-approve the cluster in single-cluster mode.
 */
@Service
public class OperatorKeyManager {

    static final String SECRET_NAME = "appbahn-operator-key";
    static final String PRIVATE_KEY_FIELD = "private-key";
    static final String PUBLIC_KEY_FIELD = "public-key";

    private static final Logger log = LoggerFactory.getLogger(OperatorKeyManager.class);

    private final KubernetesClient kubernetesClient;

    private volatile KeyPair keyPair;
    private volatile String publicKeyPem;
    private volatile UUID operatorInstanceId;

    public OperatorKeyManager(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @PostConstruct
    public void ensureKey() {
        var namespace = kubernetesClient.getNamespace();
        var existing = kubernetesClient
                .secrets()
                .inNamespace(namespace)
                .withName(SECRET_NAME)
                .get();

        if (existing != null && existing.getData() != null && existing.getData().containsKey(PRIVATE_KEY_FIELD)) {
            loadExisting(existing.getData());
            log.info("Loaded existing operator keypair from Secret {}/{}", namespace, SECRET_NAME);
        } else {
            generateAndPersist();
            log.info("Generated new operator keypair and persisted to Secret {}/{}", namespace, SECRET_NAME);
        }
    }

    public KeyPair keyPair() {
        return keyPair;
    }

    public PrivateKey privateKey() {
        return keyPair.getPrivate();
    }

    public String publicKeyPem() {
        return publicKeyPem;
    }

    public UUID operatorInstanceId() {
        return operatorInstanceId;
    }

    private void loadExisting(Map<String, String> data) {
        try {
            byte[] priv = Base64.getDecoder().decode(data.get(PRIVATE_KEY_FIELD));
            byte[] pub = Base64.getDecoder().decode(data.get(PUBLIC_KEY_FIELD));
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(priv));
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pub));
            keyPair = new KeyPair(publicKey, privateKey);
            publicKeyPem = pemEncode(publicKey.getEncoded());
            operatorInstanceId = UUID.randomUUID();
        } catch (Exception e) {
            throw new IllegalStateException("Could not load operator keypair from Secret", e);
        }
    }

    private void generateAndPersist() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            keyPair = gen.generateKeyPair();
            publicKeyPem = pemEncode(keyPair.getPublic().getEncoded());
            operatorInstanceId = UUID.randomUUID();

            var secret = new SecretBuilder()
                    .withNewMetadata()
                    .withName(SECRET_NAME)
                    .withNamespace(kubernetesClient.getNamespace())
                    .endMetadata()
                    .addToData(
                            PRIVATE_KEY_FIELD,
                            Base64.getEncoder()
                                    .encodeToString(keyPair.getPrivate().getEncoded()))
                    .addToData(
                            PUBLIC_KEY_FIELD,
                            Base64.getEncoder()
                                    .encodeToString(keyPair.getPublic().getEncoded()))
                    .withType("Opaque")
                    .build();
            kubernetesClient
                    .secrets()
                    .inNamespace(kubernetesClient.getNamespace())
                    .resource(secret)
                    .serverSideApply();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate + persist operator keypair", e);
        }
    }

    private static String pemEncode(byte[] derBytes) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(derBytes)
                + "\n-----END PUBLIC KEY-----";
    }
}

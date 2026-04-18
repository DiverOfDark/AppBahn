package eu.appbahn.operator.config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jetty.JettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds a second HTTPS connector on port 8443 for the Kubernetes admission webhook, using the
 * cert-manager TLS certificate mounted at /etc/webhook/tls.
 */
@Configuration
public class WebhookServerConfig {

    private static final Logger log = LoggerFactory.getLogger(WebhookServerConfig.class);

    @Value("${operator.webhook.tls-cert-path:/etc/webhook/tls/tls.crt}")
    private String tlsCertPath;

    @Value("${operator.webhook.tls-key-path:/etc/webhook/tls/tls.key}")
    private String tlsKeyPath;

    @Value("${operator.webhook.port:8443}")
    private int webhookPort;

    @Bean
    public JettyServerCustomizer webhookSslConnector() {
        return (Server server) -> {
            File certFile = new File(tlsCertPath);
            File keyFile = new File(tlsKeyPath);
            if (!certFile.exists() || !keyFile.exists()) {
                log.warn(
                        "Webhook TLS certificate not found at {} / {} — webhook HTTPS connector not started",
                        tlsCertPath,
                        tlsKeyPath);
                return;
            }

            try {
                KeyStore keyStore = buildKeyStoreFromPem(certFile, keyFile);

                SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
                sslContextFactory.setKeyStore(keyStore);
                sslContextFactory.setKeyStorePassword("");

                HttpConfiguration httpsConfig = new HttpConfiguration();
                httpsConfig.setSecureScheme("https");
                httpsConfig.setSecurePort(webhookPort);
                httpsConfig.addCustomizer(new SecureRequestCustomizer());

                ServerConnector sslConnector = new ServerConnector(
                        server,
                        new SslConnectionFactory(sslContextFactory, "http/1.1"),
                        new HttpConnectionFactory(httpsConfig));
                sslConnector.setPort(webhookPort);
                server.addConnector(sslConnector);

                log.info("Webhook HTTPS connector started on port {}", webhookPort);
            } catch (Exception e) {
                log.error("Failed to configure webhook HTTPS connector", e);
            }
        };
    }

    private KeyStore buildKeyStoreFromPem(File certFile, File keyFile) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certs = new ArrayList<>();
        try (InputStream is = new ByteArrayInputStream(Files.readAllBytes(certFile.toPath()))) {
            while (is.available() > 0) {
                certs.add((X509Certificate) cf.generateCertificate(is));
            }
        }

        String keyPem = Files.readString(keyFile.toPath());
        String keyBase64 = keyPem.replaceAll("-----BEGIN .*?-----", "")
                .replaceAll("-----END .*?-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

        PrivateKey privateKey = null;
        for (String algorithm : List.of("RSA", "EC", "Ed25519", "EdDSA")) {
            try {
                privateKey = KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
                break;
            } catch (Exception ignored) {
            }
        }
        if (privateKey == null) {
            throw new java.security.GeneralSecurityException("Unable to parse private key — unsupported algorithm");
        }

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("webhook", privateKey, new char[0], certs.toArray(new X509Certificate[0]));
        return ks;
    }
}

package eu.appbahn.operator.tunnel;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operator↔platform tunnel configuration bound from env vars / application.yaml.
 *
 * <p>{@code platformBaseUrl} is the full origin including scheme — e.g.
 * {@code http://appbahn-platform.appbahn-system.svc:80} in-cluster or
 * {@code https://appbahn.acme.org} behind an ingress.
 */
@ConfigurationProperties(prefix = "operator.tunnel")
public record OperatorTunnelConfig(String platformBaseUrl, String clusterName) {

    public OperatorTunnelConfig {
        if (clusterName == null || clusterName.isBlank()) {
            clusterName = "local";
        }
    }
}

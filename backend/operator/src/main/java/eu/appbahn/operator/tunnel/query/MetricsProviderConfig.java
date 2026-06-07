package eu.appbahn.operator.tunnel.query;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Operator-side metrics provider. The operator runs PromQL in-cluster, so it holds the actual
 * Prometheus {@code endpoint}. When the provider is unavailable, {@link PrometheusQueryService}
 * reports {@code available=false} with a {@link #unavailableReason() reason} the platform surfaces.
 *
 * <p>Two distinct unavailable cases:
 *
 * <ul>
 *   <li>{@code type=NONE} (the default for an unconfigured install) — no metrics provider at all.
 *   <li>{@code type=PROMETHEUS} but a blank {@code endpoint} — provider chosen but no URL set.
 * </ul>
 *
 * <p>Helm wires {@code OPERATOR_PROVIDERS_METRICS_TYPE} and {@code OPERATOR_PROVIDERS_METRICS_ENDPOINT}
 * into Spring's relaxed binding.
 */
@ConfigurationProperties(prefix = "operator.providers.metrics")
public record MetricsProviderConfig(
        @DefaultValue("NONE") MetricsProviderType type,
        @DefaultValue("") String endpoint) {

    public static final String NO_PROVIDER = "Metrics not available — no metrics provider configured";
    public static final String NO_PROMETHEUS_URL = "Metrics not available — no Prometheus URL configured";

    public boolean configured() {
        return type == MetricsProviderType.PROMETHEUS && endpoint != null && !endpoint.isBlank();
    }

    /** The distinct message explaining why metrics are unavailable, or {@code null} when configured. */
    public String unavailableReason() {
        if (type == MetricsProviderType.PROMETHEUS && endpoint != null && !endpoint.isBlank()) {
            return null;
        }
        return type == MetricsProviderType.PROMETHEUS ? NO_PROMETHEUS_URL : NO_PROVIDER;
    }
}

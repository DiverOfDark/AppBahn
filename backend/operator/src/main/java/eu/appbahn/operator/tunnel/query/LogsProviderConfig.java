package eu.appbahn.operator.tunnel.query;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Operator-side log provider. The operator runs LogsQL in-cluster, so it holds the actual Victoria
 * Logs {@code endpoint}. The provider is unavailable in two distinct cases the
 * {@link VictoriaLogsQueryService} reports separately: {@link LogsProviderType#NONE} (no provider
 * wired at all) and {@link LogsProviderType#VICTORIA_LOGS} with a blank {@code endpoint} (provider
 * selected but no URL given).
 *
 * <p>Helm wires {@code OPERATOR_PROVIDERS_LOGS_TYPE} / {@code OPERATOR_PROVIDERS_LOGS_ENDPOINT} into
 * Spring's relaxed binding.
 */
@ConfigurationProperties(prefix = "operator.providers.logs")
public record LogsProviderConfig(
        @DefaultValue("NONE") LogsProviderType type,
        @DefaultValue("") String endpoint) {

    public boolean configured() {
        return type == LogsProviderType.VICTORIA_LOGS && endpoint != null && !endpoint.isBlank();
    }
}

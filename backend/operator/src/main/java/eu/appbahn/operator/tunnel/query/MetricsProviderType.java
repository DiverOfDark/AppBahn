package eu.appbahn.operator.tunnel.query;

/** Backing metrics system the operator queries in-cluster. */
public enum MetricsProviderType {
    /** No metrics provider configured — metrics queries report "not available". */
    NONE,
    PROMETHEUS
}

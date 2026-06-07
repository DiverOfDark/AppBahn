package eu.appbahn.operator.tunnel.query;

/** Backing log system the operator queries in-cluster. {@code NONE} means no provider is wired. */
public enum LogsProviderType {
    NONE,
    VICTORIA_LOGS
}

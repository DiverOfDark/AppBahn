package eu.appbahn.platform.tunnel.command;

/**
 * Stable string constants for {@code pending_command.command_type}. The column is a {@code VARCHAR}
 * so commands stay self-describing even after a proto-schema bump renames a message.
 */
public final class CommandTypes {

    public static final String APPLY_RESOURCE_BUNDLE = "APPLY_RESOURCE_BUNDLE";
    public static final String DELETE_RESOURCE = "DELETE_RESOURCE";
    public static final String APPLY_NAMESPACE = "APPLY_NAMESPACE";
    public static final String DELETE_NAMESPACE = "DELETE_NAMESPACE";
    public static final String NUDGE_IMAGE_SOURCE = "NUDGE_IMAGE_SOURCE";
    public static final String CANCEL_BUILD = "CANCEL_BUILD";
    public static final String RETRY_BUILD = "RETRY_BUILD";
    public static final String LIST_PODS = "LIST_PODS";
    public static final String QUERY_CLUSTER_CAPACITY = "QUERY_CLUSTER_CAPACITY";
    public static final String QUERY_METRICS = "QUERY_METRICS";
    public static final String QUERY_LOGS = "QUERY_LOGS";

    private CommandTypes() {}
}

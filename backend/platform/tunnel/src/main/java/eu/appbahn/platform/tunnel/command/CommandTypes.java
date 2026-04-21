package eu.appbahn.platform.tunnel.command;

/**
 * Stable string constants for {@code pending_command.command_type}. The column is a {@code VARCHAR}
 * so commands stay self-describing even after a proto-schema bump renames a message.
 */
public final class CommandTypes {

    public static final String APPLY_RESOURCE = "APPLY_RESOURCE";
    public static final String DELETE_RESOURCE = "DELETE_RESOURCE";
    public static final String APPLY_NAMESPACE = "APPLY_NAMESPACE";
    public static final String DELETE_NAMESPACE = "DELETE_NAMESPACE";

    private CommandTypes() {}
}

package eu.appbahn.platform.tunnel.command;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fires a "command pending" notification on Postgres' LISTEN/NOTIFY bus, on the channel
 * {@code cluster_cmd_<clusterName>}. {@code NOTIFY} is transactional: the wake-up only
 * fires if the surrounding transaction commits, so a failed enqueue never generates a
 * spurious wake-up.
 *
 * <p>Channel-name format matches the Sprint 5.5 spec literally: the suffix is the cluster
 * slug. Slugs validate against {@code SlugFormat#SLUG_REGEX} ({@code [a-z][a-z0-9-]*[a-z0-9]},
 * ≤30 chars), so {@code cluster_cmd_<slug>} stays well under PostgreSQL's 63-byte identifier
 * cap. Hyphens are allowed in slugs and unquoted PostgreSQL identifiers parse them as the
 * subtraction operator, so the channel is wrapped in double quotes — symmetric with the
 * {@code LISTEN}/{@code UNLISTEN} on the receiver side.
 */
@Component
public class PendingCommandNotifier {

    /** Channel-name prefix from the Sprint 5.5 spec. The suffix is the cluster slug. */
    static final String CHANNEL_PREFIX = "cluster_cmd_";

    private final JdbcTemplate jdbc;

    public PendingCommandNotifier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Send a NOTIFY for the given cluster on the current transaction's connection.
     * Caller must already be inside a transaction — Postgres queues the notification until
     * commit. {@link Propagation#MANDATORY} enforces this contract at runtime so a stray
     * call from a non-transactional context fails fast instead of leaking a fire-and-forget
     * notify on an autocommit connection.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyCluster(String clusterName) {
        // NOTIFY's channel position takes an identifier (not a string literal), so we
        // wrap in double quotes to survive hyphens; bind params are not allowed in this
        // position. clusterName is constrained by SlugFormat#SLUG_REGEX, which excludes
        // double quotes — the replace() is defense-in-depth.
        jdbc.execute("NOTIFY " + quoteIdentifier(channelFor(clusterName)));
    }

    /** Channel name for a cluster — exposed for the listener to call LISTEN on the same string. */
    public static String channelFor(String clusterName) {
        return CHANNEL_PREFIX + clusterName;
    }

    /**
     * Wrap a PostgreSQL identifier in double quotes, escaping any embedded double quotes
     * by doubling them. Used by both NOTIFY here and LISTEN/UNLISTEN on the listener side.
     */
    public static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}

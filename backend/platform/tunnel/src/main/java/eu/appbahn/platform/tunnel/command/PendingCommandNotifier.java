package eu.appbahn.platform.tunnel.command;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues {@code NOTIFY cluster_cmd_<clusterName>} on the connection bound to the current
 * transaction. Postgres' {@code NOTIFY} is transactional: the wake-up only fires if the
 * surrounding transaction commits, so a failed enqueue never generates a spurious wake-up.
 *
 * <p>Channel-name format matches the Sprint 5.5 spec literally: the suffix is the cluster
 * slug. Slugs validate against {@code SlugFormat#SLUG_REGEX} (DNS-1123-lower, ≤30 chars), so
 * {@code cluster_cmd_<slug>} stays well under PostgreSQL's 63-byte identifier cap and needs
 * no escaping.
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
        // PostgreSQL channel names follow identifier rules; slugs are DNS-1123-lower so they
        // are valid identifiers without quoting. We still concat directly (not bind-param)
        // because NOTIFY's channel position takes an identifier, not a string literal.
        jdbc.execute("NOTIFY " + CHANNEL_PREFIX + clusterName);
    }

    /** Channel name for a cluster — exposed for the listener to call LISTEN on the same string. */
    public static String channelFor(String clusterName) {
        return CHANNEL_PREFIX + clusterName;
    }
}

package eu.appbahn.platform.tunnel.command;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-stream PostgreSQL {@code LISTEN} session. Holds a dedicated JDBC connection (taken
 * directly from the {@link DataSource}, outside Spring's transaction scope) and parks on
 * {@code PGConnection.getNotifications(timeoutMillis)} until either a notification arrives
 * or the timeout elapses. {@link #awaitNotification(long)} returns true on a wake-up,
 * false on timeout — the caller should drain {@code pending_command} either way and treat
 * the boolean only as latency-feedback.
 *
 * <p>Connection ownership: each open SSE stream takes one connection from the shared
 * Hikari pool for the lifetime of the stream. Default Hikari pool size is 10; this caps
 * concurrent operator subscriptions per replica accordingly. At single-cluster scale the
 * cap is irrelevant; before scaling to many remote clusters we'd want either a dedicated
 * listener pool or a single multiplexed listener thread, but that complexity earns its
 * keep only with 10+ active subscribers.
 *
 * <p>Notifications are wake-ups only — the caller never trusts the payload, instead it
 * runs the existing {@code SELECT … FOR UPDATE SKIP LOCKED} claim query. This preserves
 * claim-contention semantics across replicas.
 */
public class PendingCommandListener implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PendingCommandListener.class);

    private final Connection connection;
    private final PGConnection pgConnection;
    private final String channel;
    private volatile boolean closed;

    private PendingCommandListener(Connection connection, PGConnection pgConnection, String channel) {
        this.connection = connection;
        this.pgConnection = pgConnection;
        this.channel = channel;
    }

    /** Open a listener for the given cluster's notification channel. */
    public static PendingCommandListener open(DataSource dataSource, String clusterName) throws SQLException {
        String channel = PendingCommandNotifier.channelFor(clusterName);
        Connection conn = dataSource.getConnection();
        boolean ok = false;
        try {
            conn.setAutoCommit(true);
            PGConnection pg = conn.unwrap(PGConnection.class);
            try (Statement st = conn.createStatement()) {
                // Identifier — slugs are DNS-1123-lower so concat is safe.
                st.execute("LISTEN " + channel);
            }
            ok = true;
            return new PendingCommandListener(conn, pg, channel);
        } finally {
            if (!ok) {
                try {
                    conn.close();
                } catch (SQLException ignored) {
                    // best effort
                }
            }
        }
    }

    /**
     * Block up to {@code timeoutMillis} for a notification on this listener's channel.
     * Returns true if at least one notification arrived; false on plain timeout. Spurious
     * wake-ups are possible (the JDBC driver may surface them) — the caller treats both
     * outcomes as "drain now."
     *
     * @throws SQLException if the underlying connection is broken — caller must close
     *     this listener and reopen.
     */
    public boolean awaitNotification(long timeoutMillis) throws SQLException {
        if (closed) {
            return false;
        }
        var notifications = pgConnection.getNotifications((int) Math.min(timeoutMillis, Integer.MAX_VALUE));
        return notifications != null && notifications.length > 0;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try (Statement st = connection.createStatement()) {
            st.execute("UNLISTEN " + channel);
        } catch (SQLException e) {
            log.debug("UNLISTEN {} failed (closing anyway): {}", channel, e.getMessage());
        }
        try {
            connection.close();
        } catch (SQLException e) {
            log.warn("Failed to release listener connection for {}: {}", channel, e.getMessage());
        }
    }
}

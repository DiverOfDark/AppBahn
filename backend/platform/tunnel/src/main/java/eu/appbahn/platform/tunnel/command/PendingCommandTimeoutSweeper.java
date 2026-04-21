package eu.appbahn.platform.tunnel.command;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Finds unacked {@code pending_command} rows past their {@code expires_at} and delegates
 * each one to {@link PendingCommandExpirer#expire(java.util.UUID)} — which runs in its
 * own transaction so a single bad row (optimistic-lock race with operator sync,
 * malformed payload) cannot block the rest of the batch.
 *
 * <p>Runs every 30s so the UI flips stuck {@code resource_cache} rows from
 * {@code PENDING} to {@code ERROR} within roughly the command's TTL + one sweep tick.
 */
@Service
public class PendingCommandTimeoutSweeper {

    private static final Logger log = LoggerFactory.getLogger(PendingCommandTimeoutSweeper.class);

    private final PendingCommandRepository pendingCommands;
    private final PendingCommandExpirer expirer;

    public PendingCommandTimeoutSweeper(PendingCommandRepository pendingCommands, PendingCommandExpirer expirer) {
        this.pendingCommands = pendingCommands;
        this.expirer = expirer;
    }

    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void sweep() {
        List<PendingCommandEntity> expired = pendingCommands.findByAckedAtIsNullAndExpiresAtBefore(Instant.now());
        if (expired.isEmpty()) {
            return;
        }
        log.info("Sweeping {} expired unacked pending_command row(s)", expired.size());
        for (PendingCommandEntity row : expired) {
            try {
                expirer.expire(row.getId());
            } catch (RuntimeException e) {
                // Isolate per-row failures: one bad row must not stop the rest of the batch.
                log.warn(
                        "Failed to expire pending_command id={}: {} — will retry on next sweep",
                        row.getId(),
                        e.getMessage());
            }
        }
    }
}

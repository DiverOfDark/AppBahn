package eu.appbahn.platform.tunnel.command;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hourly sweep that drops {@code pending_command} rows whose {@code acked_at} is older than
 * {@link #RETENTION}. Acked rows are kept around briefly so a late operator retry can find
 * the original correlation_id; beyond that they are pure clutter.
 */
@Service
public class PendingCommandReaper {

    private static final Logger log = LoggerFactory.getLogger(PendingCommandReaper.class);
    private static final Duration RETENTION = Duration.ofHours(1);

    private final PendingCommandRepository repository;

    public PendingCommandReaper(PendingCommandRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelay = 60L * 60L * 1000L, initialDelay = 60L * 1000L)
    @Transactional
    public void reap() {
        Instant cutoff = Instant.now().minus(RETENTION);
        int deleted = repository.deleteAckedBefore(cutoff);
        if (deleted > 0) {
            log.info("Reaped {} acked pending_command rows older than {}", deleted, cutoff);
        }
    }
}

package eu.appbahn.platform.tunnel.command;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drops chunk rows from abandoned full-sync sessions. A session reaches {@code resource_cache}
 * only when its {@code complete=true} chunk arrives and {@link
 * eu.appbahn.platform.tunnel.events.PushEventsHandler} commits the set-diff; any chunk older
 * than {@link #TTL} belongs to a session the operator never finished (crash mid-sync,
 * disconnect before final chunk) and would otherwise sit forever.
 */
@Service
public class FullSyncChunkBufferSweeper {

    private static final Logger log = LoggerFactory.getLogger(FullSyncChunkBufferSweeper.class);
    static final Duration TTL = Duration.ofMinutes(15);

    private final FullSyncChunkBufferRepository repository;

    public FullSyncChunkBufferSweeper(FullSyncChunkBufferRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelay = 5L * 60L * 1000L, initialDelay = 5L * 60L * 1000L)
    @Transactional
    public void sweep() {
        Instant cutoff = Instant.now().minus(TTL);
        long deleted = repository.deleteByReceivedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Swept {} stale full_sync_chunk_buffer rows older than {}", deleted, cutoff);
        }
    }
}

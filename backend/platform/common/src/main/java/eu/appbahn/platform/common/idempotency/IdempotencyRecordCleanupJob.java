package eu.appbahn.platform.common.idempotency;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyRecordCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyRecordCleanupJob.class);

    private final IdempotencyRecordRepository repository;
    private final Clock clock;

    @Autowired
    public IdempotencyRecordCleanupJob(IdempotencyRecordRepository repository) {
        this(repository, Clock.systemUTC());
    }

    IdempotencyRecordCleanupJob(IdempotencyRecordRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanup() {
        Instant cutoff = clock.instant().minus(IdempotencyConstants.TTL);
        int deleted = repository.deleteByCreatedAtBefore(cutoff);
        log.info("Cleaned up {} idempotency-record rows older than {}", deleted, IdempotencyConstants.TTL);
    }
}

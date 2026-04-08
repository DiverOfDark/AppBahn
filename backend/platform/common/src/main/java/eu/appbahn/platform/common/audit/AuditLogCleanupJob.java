package eu.appbahn.platform.common.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditLogCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AuditLogCleanupJob.class);

    private final AuditLogRepository repository;
    private final int retentionDays;

    public AuditLogCleanupJob(
            AuditLogRepository repository, @Value("${platform.audit.retention-days:365}") int retentionDays) {
        this.repository = repository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanup() {
        var cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        repository.deleteByTimestampBefore(cutoff);
        log.info("Cleaned up audit log entries older than {} days", retentionDays);
    }
}

package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.workspace.repository.PendingInvitationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PendingInvitationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(PendingInvitationCleanupJob.class);
    private static final int STALE_DAYS = 90;

    private final PendingInvitationRepository pendingInvitationRepository;

    public PendingInvitationCleanupJob(PendingInvitationRepository pendingInvitationRepository) {
        this.pendingInvitationRepository = pendingInvitationRepository;
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupStaleInvitations() {
        var cutoff = Instant.now().minus(STALE_DAYS, ChronoUnit.DAYS);
        pendingInvitationRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Cleaned up pending invitations older than {} days", STALE_DAYS);
    }
}

package eu.appbahn.platform.tunnel.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.tunnel.ApplyResource;
import eu.appbahn.platform.api.tunnel.CommandStatus;
import eu.appbahn.platform.api.tunnel.DeleteResource;
import eu.appbahn.platform.resource.entity.ResourceCacheEntity;
import eu.appbahn.platform.resource.repository.ResourceCacheRepository;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceStatusDetail;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expires a single unacked {@code pending_command} row. Lives in its own bean so the
 * {@link Transactional} boundary isn't bypassed by self-invocation from
 * {@link PendingCommandTimeoutSweeper}. The sweeper iterates expired ids and delegates each
 * one here; a single bad row (optimistic-lock race, malformed payload) only rolls back its
 * own transaction.
 */
@Service
public class PendingCommandExpirer {

    static final String TIMEOUT_MESSAGE = "cluster unreachable";

    private static final Logger log = LoggerFactory.getLogger(PendingCommandExpirer.class);

    private final PendingCommandRepository pendingCommands;
    private final ResourceCacheRepository resourceCache;
    private final ObjectMapper mapper;

    public PendingCommandExpirer(
            PendingCommandRepository pendingCommands, ResourceCacheRepository resourceCache, ObjectMapper mapper) {
        this.pendingCommands = pendingCommands;
        this.resourceCache = resourceCache;
        this.mapper = mapper;
    }

    @Transactional
    public void expire(UUID rowId) {
        PendingCommandEntity row = pendingCommands.findById(rowId).orElse(null);
        if (row == null || row.getAckedAt() != null) {
            // Raced with a concurrent ack or a previous sweep. Nothing to do.
            return;
        }
        extractResourceSlug(row).flatMap(resourceCache::findBySlug).ifPresent(this::markCacheRowError);
        row.setAckedAt(Instant.now());
        row.setResponseStatus(CommandStatus.TIMEOUT.name());
        row.setResponseMessage(TIMEOUT_MESSAGE);
        pendingCommands.save(row);
    }

    private void markCacheRowError(ResourceCacheEntity cache) {
        // Only nudge PENDING rows. If the operator did manage to reconcile after the command
        // was considered expired, we don't want to overwrite a READY/ERROR state the sync
        // loop produced.
        if (cache.getStatus() != ResourcePhase.PENDING) {
            return;
        }
        cache.setStatus(ResourcePhase.ERROR);
        ResourceStatusDetail detail =
                cache.getStatusDetail() != null ? cache.getStatusDetail() : new ResourceStatusDetail();
        detail.setPhase(ResourcePhase.ERROR);
        detail.setMessage(TIMEOUT_MESSAGE);
        cache.setStatusDetail(detail);
        cache.setUpdatedAt(Instant.now());
        resourceCache.save(cache);
    }

    private Optional<String> extractResourceSlug(PendingCommandEntity row) {
        try {
            return switch (row.getCommandType()) {
                case CommandTypes.APPLY_RESOURCE -> {
                    ApplyResource body = mapper.readValue(row.getPayload(), ApplyResource.class);
                    String slug = body.getResource() != null
                                    && body.getResource().getMetadata() != null
                                    && body.getResource().getMetadata().getName() != null
                            ? body.getResource().getMetadata().getName()
                            : "";
                    yield slug.isEmpty() ? Optional.empty() : Optional.of(slug);
                }
                case CommandTypes.DELETE_RESOURCE -> {
                    DeleteResource body = mapper.readValue(row.getPayload(), DeleteResource.class);
                    String slug = body.getResourceSlug();
                    yield slug == null || slug.isEmpty() ? Optional.empty() : Optional.of(slug);
                }
                default -> Optional.empty();
            };
        } catch (Exception e) {
            log.warn(
                    "Could not parse payload of expired pending_command id={} type={}: {}",
                    row.getId(),
                    row.getCommandType(),
                    e.getMessage());
            return Optional.empty();
        }
    }
}

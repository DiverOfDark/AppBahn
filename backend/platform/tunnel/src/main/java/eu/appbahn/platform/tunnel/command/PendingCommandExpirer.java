package eu.appbahn.platform.tunnel.command;

import com.google.protobuf.util.JsonFormat;
import eu.appbahn.platform.resource.entity.ResourceCacheEntity;
import eu.appbahn.platform.resource.repository.ResourceCacheRepository;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceStatus;
import eu.appbahn.tunnel.v1.ApplyResource;
import eu.appbahn.tunnel.v1.CommandResponse;
import eu.appbahn.tunnel.v1.DeleteResource;
import java.nio.charset.StandardCharsets;
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
 * {@link PendingCommandTimeoutSweeper}. The sweeper iterates expired ids and delegates
 * each one here; a single bad row (optimistic-lock race, malformed payload) only rolls
 * back its own transaction.
 */
@Service
public class PendingCommandExpirer {

    static final String TIMEOUT_MESSAGE = "cluster unreachable";

    private static final Logger log = LoggerFactory.getLogger(PendingCommandExpirer.class);

    private final PendingCommandRepository pendingCommands;
    private final ResourceCacheRepository resourceCache;
    private final JsonFormat.Parser parser = JsonFormat.parser().ignoringUnknownFields();

    public PendingCommandExpirer(PendingCommandRepository pendingCommands, ResourceCacheRepository resourceCache) {
        this.pendingCommands = pendingCommands;
        this.resourceCache = resourceCache;
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
        row.setResponseStatus(CommandResponse.Status.STATUS_TIMEOUT.name());
        row.setResponseMessage(TIMEOUT_MESSAGE);
        pendingCommands.save(row);
    }

    private void markCacheRowError(ResourceCacheEntity cache) {
        // Only nudge PENDING rows. If the operator did manage to reconcile after the
        // command was considered expired, we don't want to overwrite a READY/ERROR state
        // the sync loop produced.
        if (cache.getStatus() != ResourcePhase.PENDING) {
            return;
        }
        cache.setStatus(ResourcePhase.ERROR);
        ResourceStatus detail = cache.getStatusDetail() != null ? cache.getStatusDetail() : new ResourceStatus();
        detail.setPhase(ResourcePhase.ERROR);
        detail.setMessage(TIMEOUT_MESSAGE);
        cache.setStatusDetail(detail);
        cache.setUpdatedAt(Instant.now());
        resourceCache.save(cache);
    }

    private Optional<String> extractResourceSlug(PendingCommandEntity row) {
        String json = new String(row.getPayload(), StandardCharsets.UTF_8);
        try {
            return switch (row.getCommandType()) {
                case CommandTypes.APPLY_RESOURCE -> {
                    var b = ApplyResource.newBuilder();
                    parser.merge(json, b);
                    String slug = b.getResource().getSlug();
                    yield slug.isEmpty() ? Optional.empty() : Optional.of(slug);
                }
                case CommandTypes.DELETE_RESOURCE -> {
                    var b = DeleteResource.newBuilder();
                    parser.merge(json, b);
                    String slug = b.getResourceSlug();
                    yield slug.isEmpty() ? Optional.empty() : Optional.of(slug);
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

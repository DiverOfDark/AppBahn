package eu.appbahn.platform.tunnel.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.tunnel.ApplyNamespace;
import eu.appbahn.platform.api.tunnel.ApplyResource;
import eu.appbahn.platform.api.tunnel.DeleteNamespace;
import eu.appbahn.platform.api.tunnel.DeleteResource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls {@code pending_command} for a given cluster, claims rows for the current replica,
 * and converts each to an SSE-ready frame (event type + body DTO).
 *
 * <p>The subscriber thread calls {@link #pollBatch(String, String, int)} in a loop. Rows are
 * only claimed (and therefore removed from future poll results) once they've been accepted
 * by the caller — the caller is responsible for writing the frame and bumping
 * {@code delivered_at} via {@link #markDelivered}.
 */
@Service
public class PendingCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PendingCommandDispatcher.class);
    private static final Duration CLAIM_STALENESS = Duration.ofSeconds(30);

    private final PendingCommandRepository repo;
    private final ObjectMapper mapper;

    public PendingCommandDispatcher(PendingCommandRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    /**
     * Claim up to {@code limit} pending commands for the given cluster. Rows whose previous
     * {@code claimed_by_replica} went stale (replica died mid-delivery) are re-claimed.
     */
    @Transactional
    public List<Claimed> pollBatch(String clusterName, String replicaId, int limit) {
        Instant staleBefore = Instant.now().minus(CLAIM_STALENESS);
        var candidates = repo.findClaimable(clusterName, staleBefore, limit);
        if (candidates.isEmpty()) {
            return List.of();
        }
        var out = new java.util.ArrayList<Claimed>();
        Instant now = Instant.now();
        for (var row : candidates) {
            row.setClaimedByReplica(replicaId);
            row.setClaimedAt(now);
            repo.save(row);
            Claimed claimed = toFrame(row);
            if (claimed != null) {
                out.add(claimed);
            }
        }
        return out;
    }

    @Transactional
    public void markDelivered(java.util.UUID commandId) {
        repo.findById(commandId).ifPresent(row -> {
            row.setDeliveredAt(Instant.now());
            repo.save(row);
        });
    }

    private Claimed toFrame(PendingCommandEntity row) {
        try {
            return switch (row.getCommandType()) {
                case CommandTypes.APPLY_RESOURCE -> {
                    ApplyResource body = mapper.readValue(row.getPayload(), ApplyResource.class);
                    body.setCorrelationId(row.getCorrelationId().toString());
                    yield new Claimed(row.getId(), row.getCorrelationId(), ApplyResource.EVENT_NAME, body);
                }
                case CommandTypes.DELETE_RESOURCE -> {
                    DeleteResource body = mapper.readValue(row.getPayload(), DeleteResource.class);
                    body.setCorrelationId(row.getCorrelationId().toString());
                    yield new Claimed(row.getId(), row.getCorrelationId(), DeleteResource.EVENT_NAME, body);
                }
                case CommandTypes.APPLY_NAMESPACE -> {
                    ApplyNamespace body = mapper.readValue(row.getPayload(), ApplyNamespace.class);
                    body.setCorrelationId(row.getCorrelationId().toString());
                    yield new Claimed(row.getId(), row.getCorrelationId(), ApplyNamespace.EVENT_NAME, body);
                }
                case CommandTypes.DELETE_NAMESPACE -> {
                    DeleteNamespace body = mapper.readValue(row.getPayload(), DeleteNamespace.class);
                    body.setCorrelationId(row.getCorrelationId().toString());
                    yield new Claimed(row.getId(), row.getCorrelationId(), DeleteNamespace.EVENT_NAME, body);
                }
                default -> {
                    log.warn("Unknown pending_command.command_type: {}", row.getCommandType());
                    yield null;
                }
            };
        } catch (Exception e) {
            log.warn(
                    "Failed to parse pending_command payload id={} type={}: {}",
                    row.getId(),
                    row.getCommandType(),
                    e.getMessage());
            return null;
        }
    }

    /**
     * One dispatchable command. {@code eventType} is the SSE event-name constant from the
     * payload DTO (e.g. {@link ApplyResource#EVENT_NAME}); {@code body} is the matching DTO —
     * the caller serialises it straight onto an SSE frame.
     */
    public record Claimed(java.util.UUID commandId, java.util.UUID correlationId, String eventType, Object body) {}
}

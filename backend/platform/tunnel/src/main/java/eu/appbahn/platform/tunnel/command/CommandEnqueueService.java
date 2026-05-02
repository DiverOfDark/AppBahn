package eu.appbahn.platform.tunnel.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single entry point for enqueueing platform→operator commands. Callers pick a
 * {@link CommandTypes} tag and a DTO payload; this service JSON-encodes the payload,
 * persists the row, fires a {@code NOTIFY cluster_cmd_<clusterName>} on the same
 * transaction so the subscribing replica's listener wakes immediately on commit, and
 * returns the assigned correlation id. Draining onto the open SSE stream is the concern
 * of {@code PendingCommandDispatcher}.
 */
@Service
public class CommandEnqueueService {

    /** How long a command may remain unacked before the sweeper gives up and marks it ERROR. */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final PendingCommandRepository repo;
    private final ObjectMapper mapper;
    private final PendingCommandNotifier notifier;

    public CommandEnqueueService(PendingCommandRepository repo, ObjectMapper mapper, PendingCommandNotifier notifier) {
        this.repo = repo;
        this.mapper = mapper;
        this.notifier = notifier;
    }

    @Transactional
    public UUID enqueue(String clusterName, String commandType, Object payload) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        UUID correlationId = UuidCreator.getTimeOrderedEpoch();

        var entity = new PendingCommandEntity();
        entity.setId(id);
        entity.setClusterName(clusterName);
        entity.setCorrelationId(correlationId);
        entity.setCommandType(commandType);
        entity.setPayload(encode(payload));
        entity.setEnqueuedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(DEFAULT_TTL));
        repo.save(entity);
        // NOTIFY on the same connection — Postgres queues it until commit, so a rolled-back
        // enqueue produces no wake-up.
        notifier.notifyCluster(clusterName);
        return correlationId;
    }

    private byte[] encode(Object payload) {
        try {
            return mapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not JSON-encode command payload", e);
        }
    }
}

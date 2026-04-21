package eu.appbahn.platform.tunnel.command;

import com.github.f4b6a3.uuid.UuidCreator;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single entry point for enqueuing platform→operator commands. Callers pick a
 * {@link CommandTypes} tag and a protobuf payload; this service persists the row
 * and returns the assigned correlation id. Draining onto the open
 * {@code SubscribeCommands} stream is the concern of {@code PendingCommandDispatcher}.
 */
@Service
public class CommandEnqueueService {

    /** How long a command may remain unacked before the sweeper gives up and marks it ERROR. */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final PendingCommandRepository repo;
    private final JsonFormat.Printer printer = JsonFormat.printer().omittingInsignificantWhitespace();

    public CommandEnqueueService(PendingCommandRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public UUID enqueue(String clusterName, String commandType, Message payload) {
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
        return correlationId;
    }

    private byte[] encode(Message payload) {
        try {
            return printer.print(payload).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not JSON-encode command payload", e);
        }
    }
}

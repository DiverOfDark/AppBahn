package eu.appbahn.platform.tunnel.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.tunnel.CommandResponsePayload;
import eu.appbahn.platform.api.tunnel.CommandStatus;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Round-trip helper for read-style commands ({@code LIST_PODS}, {@code QUERY_CLUSTER_CAPACITY}):
 * enqueues the command, blocks the request thread on a tight DB poll until the operator's
 * ack arrives, and deserialises the typed {@link CommandResponsePayload} subtype back to
 * the caller. Action commands keep using {@link CommandEnqueueService#enqueue} directly —
 * they don't need a response body.
 *
 * <p>Polling beats LISTEN/NOTIFY for the response path: an ack is at most one short HTTP
 * call away (operator → ackCommand → row update), and we're already paying for the
 * dispatcher's NOTIFY on enqueue. A 50ms poll keeps round-trip latency well under one
 * tick for the common case where the operator is responsive.
 */
@Service
public class CommandResponseAwaiter {

    /** How often we re-check the row. Cheap PK lookup; 50ms keeps tail latency tight. */
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    private final CommandEnqueueService enqueueService;
    private final PendingCommandRepository repo;
    private final ObjectMapper mapper;

    public CommandResponseAwaiter(
            CommandEnqueueService enqueueService, PendingCommandRepository repo, ObjectMapper mapper) {
        this.enqueueService = enqueueService;
        this.repo = repo;
        this.mapper = mapper;
    }

    /**
     * Enqueue a read-style command and block until the operator acks with a typed payload.
     *
     * @param clusterName  destination cluster
     * @param commandType  one of the {@link CommandTypes} string constants
     * @param command      command DTO (will be JSON-encoded)
     * @param responseType expected subtype of the ack payload
     * @param timeout      max time to wait for the ack
     * @param <T>          response payload subtype
     * @return the deserialised payload
     * @throws CommandTimeoutException if the wait elapses
     * @throws CommandFailedException  if the operator acks with a non-OK status, or returns no payload
     */
    public <T extends CommandResponsePayload> T enqueueAndAwait(
            String clusterName, String commandType, Object command, Class<T> responseType, Duration timeout) {
        UUID correlationId = enqueueService.enqueue(clusterName, commandType, command);
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            var row = repo.findByCorrelationId(correlationId).orElse(null);
            if (row != null && row.getAckedAt() != null) {
                return decode(row, responseType);
            }
            if (System.nanoTime() >= deadline) {
                throw new CommandTimeoutException(commandType, clusterName, correlationId);
            }
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CommandFailedException(commandType, clusterName, "interrupted waiting for ack");
            }
        }
    }

    private <T extends CommandResponsePayload> T decode(PendingCommandEntity row, Class<T> responseType) {
        if (!CommandStatus.OK.name().equals(row.getResponseStatus())) {
            throw new CommandFailedException(
                    row.getCommandType(),
                    row.getClusterName(),
                    "operator returned " + row.getResponseStatus() + ": "
                            + (row.getResponseMessage() != null ? row.getResponseMessage() : ""));
        }
        if (row.getResponsePayload() == null) {
            throw new CommandFailedException(
                    row.getCommandType(), row.getClusterName(), "operator ack carried no payload");
        }
        try {
            return mapper.readValue(row.getResponsePayload(), responseType);
        } catch (Exception e) {
            throw new CommandFailedException(
                    row.getCommandType(), row.getClusterName(), "could not decode response payload: " + e.getMessage());
        }
    }

    /** The operator did not ack in time — usually means the cluster's tunnel is down. */
    public static class CommandTimeoutException extends RuntimeException {
        public CommandTimeoutException(String commandType, String clusterName, UUID correlationId) {
            super("Operator did not ack command type=" + commandType + " cluster=" + clusterName + " correlationId="
                    + correlationId + " within the budget");
        }
    }

    /** The operator acked but with a non-OK status, or the payload was missing/malformed. */
    public static class CommandFailedException extends RuntimeException {
        public CommandFailedException(String commandType, String clusterName, String detail) {
            super("Command " + commandType + " on cluster " + clusterName + " failed: " + detail);
        }
    }
}

package eu.appbahn.platform.tunnel.command;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import eu.appbahn.tunnel.v1.ApplyNamespace;
import eu.appbahn.tunnel.v1.ApplyResource;
import eu.appbahn.tunnel.v1.DeleteNamespace;
import eu.appbahn.tunnel.v1.DeleteResource;
import eu.appbahn.tunnel.v1.PlatformMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls {@code pending_command} for a given cluster, claims rows for the current replica,
 * and converts each to a {@link PlatformMessage} frame ready for serialisation onto an
 * open {@code SubscribeCommands} stream.
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
    private final JsonFormat.Parser parser = JsonFormat.parser().ignoringUnknownFields();

    public PendingCommandDispatcher(PendingCommandRepository repo) {
        this.repo = repo;
    }

    /**
     * Claim up to {@code limit} pending commands for the given cluster. Rows whose previous
     * {@code claimed_by_replica} went stale (replica died mid-delivery) are re-claimed.
     */
    @Transactional
    public List<Claimed> pollBatch(String clusterName, String replicaId, int limit) {
        Instant staleBefore = Instant.now().minus(CLAIM_STALENESS);
        var candidates = repo.findClaimable(clusterName, staleBefore);
        if (candidates.isEmpty()) {
            return List.of();
        }
        var out = new java.util.ArrayList<Claimed>();
        Instant now = Instant.now();
        for (var row : candidates) {
            if (out.size() >= limit) break;
            row.setClaimedByReplica(replicaId);
            row.setClaimedAt(now);
            repo.save(row);
            PlatformMessage frame = toFrame(row);
            if (frame != null) {
                out.add(new Claimed(row.getId(), row.getCorrelationId(), frame));
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

    private PlatformMessage toFrame(PendingCommandEntity row) {
        String json = new String(row.getPayload(), StandardCharsets.UTF_8);
        try {
            return switch (row.getCommandType()) {
                case CommandTypes.APPLY_RESOURCE -> {
                    ApplyResource.Builder b = ApplyResource.newBuilder();
                    parser.merge(json, b);
                    b.setCorrelationId(row.getCorrelationId().toString());
                    yield PlatformMessage.newBuilder().setApplyResource(b).build();
                }
                case CommandTypes.DELETE_RESOURCE -> {
                    DeleteResource.Builder b = DeleteResource.newBuilder();
                    parser.merge(json, b);
                    b.setCorrelationId(row.getCorrelationId().toString());
                    yield PlatformMessage.newBuilder().setDeleteResource(b).build();
                }
                case CommandTypes.APPLY_NAMESPACE -> {
                    ApplyNamespace.Builder b = ApplyNamespace.newBuilder();
                    parser.merge(json, b);
                    b.setCorrelationId(row.getCorrelationId().toString());
                    yield PlatformMessage.newBuilder().setApplyNamespace(b).build();
                }
                case CommandTypes.DELETE_NAMESPACE -> {
                    DeleteNamespace.Builder b = DeleteNamespace.newBuilder();
                    parser.merge(json, b);
                    b.setCorrelationId(row.getCorrelationId().toString());
                    yield PlatformMessage.newBuilder().setDeleteNamespace(b).build();
                }
                default -> {
                    log.warn("Unknown pending_command.command_type: {}", row.getCommandType());
                    yield null;
                }
            };
        } catch (InvalidProtocolBufferException e) {
            log.warn(
                    "Failed to parse pending_command payload id={} type={}: {}",
                    row.getId(),
                    row.getCommandType(),
                    e.getMessage());
            return null;
        }
    }

    public record Claimed(java.util.UUID commandId, java.util.UUID correlationId, PlatformMessage frame) {}
}

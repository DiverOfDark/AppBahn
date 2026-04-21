package eu.appbahn.operator.tunnel;

import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.tunnel.v1.FullResourceSyncChunk;
import eu.appbahn.tunnel.v1.OperatorEvent;
import eu.appbahn.tunnel.v1.PushEventsAck;
import eu.appbahn.tunnel.v1.PushEventsRequest;
import eu.appbahn.tunnel.v1.ResourceDeletedBatch;
import eu.appbahn.tunnel.v1.ResourceSyncBatch;
import eu.appbahn.tunnel.v1.ResourceSyncItem;
import eu.appbahn.tunnel.wire.ResourceWireMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Publishes reconcile batches, deletions and full-sync chunks via {@code PushEvents}.
 */
@Service
public class OperatorEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OperatorEventPublisher.class);
    private static final int FULL_SYNC_CHUNK_SIZE = 100;

    private final OperatorTunnelClient tunnelClient;
    private final OperatorTunnelConfig tunnelConfig;

    public OperatorEventPublisher(OperatorTunnelClient tunnelClient, OperatorTunnelConfig tunnelConfig) {
        this.tunnelClient = tunnelClient;
        this.tunnelConfig = tunnelConfig;
    }

    /** Emit a single pre-built {@link OperatorEvent}. Used by the admission webhook. */
    public void emit(OperatorEvent event) throws IOException {
        send(List.of(event));
    }

    public void emitSync(ResourceCrd crd) throws IOException {
        ResourceSyncItem item = toSyncItem(crd);
        var event = OperatorEvent.newBuilder()
                .setResourceSyncBatch(ResourceSyncBatch.newBuilder().addItems(item))
                .build();
        send(List.of(event));
    }

    public void emitDeleted(String slug) throws IOException {
        var event = OperatorEvent.newBuilder()
                .setResourceDeletedBatch(ResourceDeletedBatch.newBuilder().addResourceSlugs(slug))
                .build();
        send(List.of(event));
    }

    /**
     * Emit a full set of Resource CRs as one or more {@link FullResourceSyncChunk}s,
     * the last of which is marked {@code complete=true}. Chunks may land on different
     * platform replicas — the platform buffers them and commits set-diff on complete.
     */
    public void emitFullSync(List<ResourceCrd> crds) throws IOException {
        UUID sessionId = UUID.randomUUID();
        if (crds.isEmpty()) {
            // Still send a complete=true chunk so the platform prunes stale rows.
            var event = OperatorEvent.newBuilder()
                    .setFullResourceSyncChunk(FullResourceSyncChunk.newBuilder()
                            .setSyncSessionId(sessionId.toString())
                            .setChunkIndex(0)
                            .setComplete(true))
                    .build();
            send(List.of(event));
            return;
        }

        List<OperatorEvent> events = new ArrayList<>();
        int chunkIndex = 0;
        for (int i = 0; i < crds.size(); i += FULL_SYNC_CHUNK_SIZE) {
            List<ResourceCrd> slice = crds.subList(i, Math.min(i + FULL_SYNC_CHUNK_SIZE, crds.size()));
            boolean isLast = i + FULL_SYNC_CHUNK_SIZE >= crds.size();
            var chunk = FullResourceSyncChunk.newBuilder()
                    .setSyncSessionId(sessionId.toString())
                    .setChunkIndex(chunkIndex++)
                    .setComplete(isLast);
            for (ResourceCrd crd : slice) {
                chunk.addItems(toSyncItem(crd));
            }
            events.add(
                    OperatorEvent.newBuilder().setFullResourceSyncChunk(chunk).build());
        }
        send(events);
    }

    private void send(List<OperatorEvent> events) throws IOException {
        var req = PushEventsRequest.newBuilder()
                .setClusterName(tunnelConfig.clusterName())
                .addAllEvents(events)
                .build();
        tunnelClient.unary("PushEvents", req, PushEventsAck.newBuilder());
    }

    public ResourceSyncItem toSyncItem(ResourceCrd crd) {
        return ResourceWireMapper.toSyncItem(crd);
    }
}

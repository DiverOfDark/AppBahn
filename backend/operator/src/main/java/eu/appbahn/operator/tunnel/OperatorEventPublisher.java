package eu.appbahn.operator.tunnel;

import eu.appbahn.operator.tunnel.client.model.FullResourceSyncChunk;
import eu.appbahn.operator.tunnel.client.model.OperatorEvent;
import eu.appbahn.operator.tunnel.client.model.PushEventsAck;
import eu.appbahn.operator.tunnel.client.model.PushEventsRequest;
import eu.appbahn.operator.tunnel.client.model.ResourceDeletedBatch;
import eu.appbahn.operator.tunnel.client.model.ResourceSyncBatch;
import eu.appbahn.operator.tunnel.client.model.ResourceSyncItem;
import eu.appbahn.shared.crd.ResourceCrd;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Publishes reconcile batches, deletions and full-sync chunks via {@link TunnelApiClient#pushEvents}. */
@Service
public class OperatorEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OperatorEventPublisher.class);
    private static final int FULL_SYNC_CHUNK_SIZE = 100;

    private final TunnelApiClient tunnelApiClient;
    private final OperatorTunnelConfig tunnelConfig;

    public OperatorEventPublisher(TunnelApiClient tunnelApiClient, OperatorTunnelConfig tunnelConfig) {
        this.tunnelApiClient = tunnelApiClient;
        this.tunnelConfig = tunnelConfig;
    }

    /** Emit a single pre-built event. Used by the admission webhook. */
    public void emit(OperatorEvent event) {
        send(List.of(event));
    }

    public void emitSync(ResourceCrd crd) {
        var batch = new ResourceSyncBatch();
        batch.getItems().add(toSyncItem(crd));
        send(List.of(batch));
    }

    public void emitDeleted(String slug) {
        var event = new ResourceDeletedBatch();
        event.getResourceSlugs().add(slug);
        send(List.of(event));
    }

    /**
     * Emit a full set of Resource CRs as one or more {@link FullResourceSyncChunk}s, the last
     * of which is marked {@code complete=true}. Chunks may land on different platform
     * replicas — the platform buffers them and commits the set-diff on complete.
     */
    public void emitFullSync(List<ResourceCrd> crds) {
        UUID sessionId = UUID.randomUUID();
        if (crds.isEmpty()) {
            var chunk = new FullResourceSyncChunk();
            chunk.setSyncSessionId(sessionId.toString());
            chunk.setChunkIndex(0);
            chunk.setComplete(true);
            send(List.of(chunk));
            return;
        }

        List<OperatorEvent> events = new ArrayList<>();
        int chunkIndex = 0;
        for (int i = 0; i < crds.size(); i += FULL_SYNC_CHUNK_SIZE) {
            List<ResourceCrd> slice = crds.subList(i, Math.min(i + FULL_SYNC_CHUNK_SIZE, crds.size()));
            boolean isLast = i + FULL_SYNC_CHUNK_SIZE >= crds.size();
            var chunk = new FullResourceSyncChunk();
            chunk.setSyncSessionId(sessionId.toString());
            chunk.setChunkIndex(chunkIndex++);
            chunk.setComplete(isLast);
            for (ResourceCrd crd : slice) {
                chunk.getItems().add(toSyncItem(crd));
            }
            events.add(chunk);
        }
        send(events);
    }

    private void send(List<OperatorEvent> events) {
        var req = new PushEventsRequest();
        req.setClusterName(tunnelConfig.clusterName());
        req.setEvents(new ArrayList<>(events));
        PushEventsAck ack = tunnelApiClient.pushEvents(req);
        if (ack.getAcceptedCount() != events.size()) {
            log.warn("Platform accepted {} of {} events — possible data loss", ack.getAcceptedCount(), events.size());
        }
    }

    public ResourceSyncItem toSyncItem(ResourceCrd crd) {
        var item = new ResourceSyncItem();
        item.setResource(crd);
        if (crd.getMetadata() != null) {
            if (crd.getMetadata().getGeneration() != null) {
                item.setGeneration(crd.getMetadata().getGeneration());
            }
            if (crd.getMetadata().getResourceVersion() != null) {
                item.setResourceVersion(crd.getMetadata().getResourceVersion());
            }
        }
        return item;
    }
}

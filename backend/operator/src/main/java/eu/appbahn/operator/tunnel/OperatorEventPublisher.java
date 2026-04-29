package eu.appbahn.operator.tunnel;

import eu.appbahn.operator.tunnel.client.model.FullResourceSyncChunk;
import eu.appbahn.operator.tunnel.client.model.ImageSourceDeletedBatch;
import eu.appbahn.operator.tunnel.client.model.ImageSourceSyncBatch;
import eu.appbahn.operator.tunnel.client.model.ImageSourceSyncItem;
import eu.appbahn.operator.tunnel.client.model.OperatorEvent;
import eu.appbahn.operator.tunnel.client.model.ResourceDeletedBatch;
import eu.appbahn.operator.tunnel.client.model.ResourceSyncBatch;
import eu.appbahn.operator.tunnel.client.model.ResourceSyncItem;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Publishes reconcile batches, deletions and full-sync chunks to the platform tunnel.
 *
 * <p>All emit methods are non-blocking: they hand the batch to {@link OperatorEventQueue}
 * which delivers it asynchronously on a virtual-thread drainer with retries and
 * exponential backoff. This keeps the JOSDK reconcile loop and the admission webhook
 * decoupled from tunnel latency.
 */
@Service
public class OperatorEventPublisher {

    private static final int FULL_SYNC_CHUNK_SIZE = 100;

    private final OperatorEventQueue queue;

    public OperatorEventPublisher(OperatorEventQueue queue) {
        this.queue = queue;
    }

    /** Emit a single pre-built event. Used by the admission webhook. */
    public void emit(OperatorEvent event) {
        queue.enqueue(List.of(event));
    }

    public void emitSync(ResourceCrd crd) {
        var batch = new ResourceSyncBatch();
        batch.getItems().add(toSyncItem(crd));
        queue.enqueue(List.of(batch));
    }

    public void emitDeleted(String slug) {
        var event = new ResourceDeletedBatch();
        event.getResourceSlugs().add(slug);
        queue.enqueue(List.of(event));
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
            queue.enqueue(List.of(chunk));
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
        queue.enqueue(events);
    }

    public void emitImageSourceSync(ImageSourceCrd crd) {
        var batch = new ImageSourceSyncBatch();
        batch.getItems().add(toImageSourceSyncItem(crd));
        queue.enqueue(List.of(batch));
    }

    public void emitImageSourceDeleted(String slug) {
        var event = new ImageSourceDeletedBatch();
        event.getImageSourceSlugs().add(slug);
        queue.enqueue(List.of(event));
    }

    public ImageSourceSyncItem toImageSourceSyncItem(ImageSourceCrd crd) {
        var item = new ImageSourceSyncItem();
        item.setImageSource(crd);
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

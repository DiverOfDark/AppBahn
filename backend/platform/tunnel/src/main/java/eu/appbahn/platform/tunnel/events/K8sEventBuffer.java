package eu.appbahn.platform.tunnel.events;

import eu.appbahn.platform.api.resource.LogStreamEventFrame;
import eu.appbahn.platform.api.tunnel.ResourceK8sEvent;
import eu.appbahn.platform.resource.service.K8sEventSupplier;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory ring buffer of recent Kubernetes events per Resource, fed by the operator over the
 * tunnel ({@link ResourceK8sEvent}) and drained by open {@code /logs/stream} SSE connections via the
 * {@link K8sEventSupplier} SPI.
 *
 * <p>Each buffered event carries a monotonic arrival timestamp so a streaming connection can
 * forward only events newer than its cursor. The buffer is bounded ({@value #MAX_PER_RESOURCE}
 * events per Resource) and trimmed by age ({@value #RETENTION_SECONDS}s) on every write — events
 * are an ephemeral live feed, not a persisted log.
 *
 * <p>Single-replica affinity: a stream only replays events delivered to the same platform replica
 * that received them. The operator's tunnel session pins to one subscribing replica, so all of a
 * cluster's PushEvents land there; a stream opened on a different replica still receives newly
 * arriving events once that replica becomes the cluster's session owner. This matches the live
 * nature of the feed and keeps the hot path off the database.
 */
@Service
public class K8sEventBuffer implements K8sEventSupplier {

    private static final int MAX_PER_RESOURCE = 500;
    private static final long RETENTION_SECONDS = 600;

    private record Buffered(Instant arrivedAt, LogStreamEventFrame frame) {}

    private final Map<String, Deque<Buffered>> byResource = new ConcurrentHashMap<>();

    /** Record an event forwarded by the operator. */
    public void record(String clusterName, ResourceK8sEvent event) {
        String key = key(clusterName, event.getNamespace(), event.getResourceSlug());
        Deque<Buffered> buffer = byResource.computeIfAbsent(key, k -> new ArrayDeque<>());
        var frame = new LogStreamEventFrame();
        frame.setEventType(event.getEventType());
        frame.setReason(event.getReason());
        frame.setMessage(event.getMessage());
        frame.setInvolvedKind(event.getInvolvedKind());
        frame.setInvolvedName(event.getInvolvedName());
        frame.setPod(event.getPod());
        frame.setCount(event.getCount());
        frame.setEventTime(event.getEventTime());
        synchronized (buffer) {
            buffer.addLast(new Buffered(Instant.now(), frame));
            trim(buffer);
        }
    }

    @Override
    public List<LogStreamEventFrame> drainSince(
            String clusterName, String namespace, String resourceSlug, Instant after) {
        Deque<Buffered> buffer = byResource.get(key(clusterName, namespace, resourceSlug));
        if (buffer == null) {
            return List.of();
        }
        List<LogStreamEventFrame> out = new ArrayList<>();
        synchronized (buffer) {
            for (Buffered b : buffer) {
                if (b.arrivedAt().isAfter(after)) {
                    out.add(b.frame());
                }
            }
        }
        return out;
    }

    @Override
    public Instant latestArrival(String clusterName, String namespace, String resourceSlug, Instant after) {
        Deque<Buffered> buffer = byResource.get(key(clusterName, namespace, resourceSlug));
        if (buffer == null) {
            return after;
        }
        synchronized (buffer) {
            Buffered last = buffer.peekLast();
            if (last != null && last.arrivedAt().isAfter(after)) {
                return last.arrivedAt();
            }
        }
        return after;
    }

    private void trim(Deque<Buffered> buffer) {
        Instant cutoff = Instant.now().minusSeconds(RETENTION_SECONDS);
        while (!buffer.isEmpty()
                && (buffer.size() > MAX_PER_RESOURCE
                        || buffer.peekFirst().arrivedAt().isBefore(cutoff))) {
            buffer.removeFirst();
        }
    }

    private static String key(String clusterName, String namespace, String resourceSlug) {
        return clusterName + "/" + namespace + "/" + resourceSlug;
    }
}

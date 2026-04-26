package eu.appbahn.operator.tunnel;

import eu.appbahn.operator.tunnel.client.model.OperatorEvent;
import eu.appbahn.operator.tunnel.client.model.PushEventsAck;
import eu.appbahn.operator.tunnel.client.model.PushEventsRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Decouples operator→platform event publishing from the reconcile loop.
 *
 * <p>Producers ({@link OperatorEventPublisher}) call {@link #enqueue(List)} which is
 * non-blocking: it offers the batch to a bounded {@link BlockingQueue} and returns. A
 * single virtual-thread drainer pops batches and pushes them via {@link TunnelApiClient}.
 * Failed pushes are re-scheduled with an exponential backoff up to {@link #MAX_ATTEMPTS}
 * times before being dropped with a WARN log.
 *
 * <p>Durability: in-memory only. The reconcile loop is the recovery mechanism — any batch
 * lost on operator restart will be re-emitted on the next reconcile tick (and {@code
 * FullSyncService} runs a sweep on startup as a safety net).
 *
 * <p>Back-pressure: when the queue is full, the oldest batch is dropped to make room. This
 * keeps the most recent state changes flowing rather than letting the queue fossilize on
 * stale events while the platform is unreachable.
 */
@Component
public class OperatorEventQueue {

    private static final Logger log = LoggerFactory.getLogger(OperatorEventQueue.class);

    /** Max in-flight batches. ~10k batches × tens of bytes each = bounded memory footprint. */
    static final int QUEUE_CAPACITY = 10_000;

    /** Max retry attempts before a batch is dropped. */
    static final int MAX_ATTEMPTS = 5;

    /** Backoff schedule. {@code BACKOFF[i]} is the delay before attempt {@code i+2}. */
    static final Duration[] BACKOFF = {
        Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10)
    };

    /** Time budget for the {@link #shutdown} drain. */
    static final Duration SHUTDOWN_DRAIN_BUDGET = Duration.ofSeconds(30);

    private final TunnelApiClient tunnelApiClient;
    private final OperatorTunnelConfig tunnelConfig;
    private final BlockingQueue<QueuedBatch> queue;
    private final ScheduledExecutorService scheduler;
    private final Thread drainer;
    private volatile boolean running = true;

    @Autowired
    public OperatorEventQueue(TunnelApiClient tunnelApiClient, OperatorTunnelConfig tunnelConfig) {
        this(tunnelApiClient, tunnelConfig, QUEUE_CAPACITY);
    }

    /** Test seam — allows shrinking the queue capacity. */
    OperatorEventQueue(TunnelApiClient tunnelApiClient, OperatorTunnelConfig tunnelConfig, int capacity) {
        this.tunnelApiClient = tunnelApiClient;
        this.tunnelConfig = tunnelConfig;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "operator-event-queue-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.drainer = Thread.ofVirtual().name("operator-event-queue-drainer").unstarted(this::drainLoop);
    }

    @PostConstruct
    void start() {
        drainer.start();
    }

    /**
     * Hand a batch to the queue. Non-blocking. On overflow, the oldest batch is evicted
     * with a WARN log. Returns {@code false} only when the queue itself is shutting down
     * and refuses new work.
     */
    public boolean enqueue(List<OperatorEvent> events) {
        if (!running) {
            log.warn("Queue is shutting down; dropping batch of {} events", events.size());
            return false;
        }
        var batch = new QueuedBatch(List.copyOf(events), 1);
        return offer(batch);
    }

    private boolean offer(QueuedBatch batch) {
        while (!queue.offer(batch)) {
            QueuedBatch evicted = queue.poll();
            if (evicted == null) {
                continue;
            }
            log.warn(
                    "Operator event queue full ({} batches); dropped oldest batch of {} events on attempt {}",
                    QUEUE_CAPACITY,
                    evicted.events.size(),
                    evicted.attempt);
        }
        return true;
    }

    private void drainLoop() {
        while (running || !queue.isEmpty()) {
            QueuedBatch batch;
            try {
                batch = queue.poll(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (batch == null) {
                continue;
            }
            push(batch);
        }
    }

    private void push(QueuedBatch batch) {
        try {
            var req = new PushEventsRequest();
            req.setClusterName(tunnelConfig.clusterName());
            req.setEvents(new ArrayList<>(batch.events));
            PushEventsAck ack = tunnelApiClient.pushEvents(req);
            if (ack.getAcceptedCount() != batch.events.size()) {
                log.warn(
                        "Platform accepted {} of {} events — possible data loss",
                        ack.getAcceptedCount(),
                        batch.events.size());
            }
        } catch (Exception e) {
            handleFailure(batch, e);
        }
    }

    private void handleFailure(QueuedBatch batch, Exception cause) {
        if (batch.attempt >= MAX_ATTEMPTS) {
            log.warn(
                    "Dropping batch of {} events after {} failed attempts: {}",
                    batch.events.size(),
                    batch.attempt,
                    cause.getMessage());
            return;
        }
        Duration delay = BACKOFF[Math.min(batch.attempt - 1, BACKOFF.length - 1)];
        log.debug(
                "Push failed (attempt {}/{}); retrying in {}: {}",
                batch.attempt,
                MAX_ATTEMPTS,
                delay,
                cause.getMessage());
        var retried = new QueuedBatch(batch.events, batch.attempt + 1);
        if (!running) {
            // Shutting down — re-enqueue immediately so the shutdown drain still pushes it.
            offer(retried);
            return;
        }
        scheduler.schedule(() -> offer(retried), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        running = false;
        long deadline = System.nanoTime() + SHUTDOWN_DRAIN_BUDGET.toNanos();
        try {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos > 0) {
                drainer.join(Duration.ofNanos(remainingNanos));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        scheduler.shutdownNow();
        if (drainer.isAlive()) {
            log.warn(
                    "Drainer still alive after {} shutdown budget; {} batches not flushed",
                    SHUTDOWN_DRAIN_BUDGET,
                    queue.size());
            drainer.interrupt();
        }
    }

    int size() {
        return queue.size();
    }

    private record QueuedBatch(List<OperatorEvent> events, int attempt) {}
}

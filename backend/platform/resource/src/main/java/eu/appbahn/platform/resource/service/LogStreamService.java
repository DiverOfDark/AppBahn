package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.resource.LogStreamEventFrame;
import eu.appbahn.platform.api.resource.LogStreamLogFrame;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.service.NamespaceService;
import eu.appbahn.shared.model.MemberRole;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Opens a Server-Sent Events stream of live container logs ({@code log} frames) and Kubernetes
 * events ({@code k8s_event} frames) for a Resource. Permission gate: VIEWER on the Resource's
 * environment — same level as reading logs or metrics.
 *
 * <p>The stream is driven by a per-connection virtual thread (mirroring the operator tunnel's
 * command drain): it polls {@link LogStreamSupplier} for log lines newer than its cursor and
 * {@link K8sEventSupplier} for buffered events, writing each as an SSE frame. Keepalive frames keep
 * the connection from idling out. The loop self-polices and exits cleanly on client disconnect or
 * platform shutdown.
 *
 * <p>Graceful degradation: when no log provider is configured the {@code log} channel stays silent
 * and only {@code k8s_event} frames flow. A stream requesting only {@code log} with no provider is
 * still a valid, keepalive-only connection rather than an error.
 */
@Service
public class LogStreamService {

    private static final Logger log = LoggerFactory.getLogger(LogStreamService.class);

    /** SSE event names. Mirror {@link LogStreamFrameType#wireName()}. */
    static final String KEEPALIVE_EVENT = "keepalive";

    private static final long POLL_INTERVAL_MS = 2_000;
    private static final long KEEPALIVE_MS = 15_000;
    private static final long EMITTER_TIMEOUT_MS = Duration.ofHours(24).toMillis();

    /** Cap on log lines pulled per poll round so a noisy pod can't wedge the loop. */
    private static final int TAIL_BATCH = 500;

    private final ResourcePermissionHelper resourcePermissionHelper;
    private final NamespaceService namespaceService;
    private final LogStreamSupplier logStreamSupplier;
    private final K8sEventSupplier k8sEventSupplier;

    private final java.util.Map<UUID, SseEmitter> activeEmitters = new ConcurrentHashMap<>();
    private volatile boolean shuttingDown = false;

    public LogStreamService(
            ResourcePermissionHelper resourcePermissionHelper,
            NamespaceService namespaceService,
            LogStreamSupplier logStreamSupplier,
            K8sEventSupplier k8sEventSupplier) {
        this.resourcePermissionHelper = resourcePermissionHelper;
        this.namespaceService = namespaceService;
        this.logStreamSupplier = logStreamSupplier;
        this.k8sEventSupplier = k8sEventSupplier;
    }

    public SseEmitter stream(
            String slug, String container, String pod, OffsetDateTime since, String types, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.VIEWER);
        EnvironmentEntity env = resolved.env();
        String clusterName = env.getTargetCluster();
        String namespace = namespaceService.computeNamespace(env.getSlug());
        Set<LogStreamFrameType> requested = LogStreamFrameType.parse(types);

        UUID streamId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitter.onCompletion(() -> activeEmitters.remove(streamId));
        emitter.onTimeout(() -> {
            emitter.complete();
            activeEmitters.remove(streamId);
        });
        emitter.onError(e -> activeEmitters.remove(streamId));
        activeEmitters.put(streamId, emitter);

        long sinceEpochSeconds = since != null
                ? since.toInstant().getEpochSecond()
                : Instant.now().getEpochSecond();
        // The log channel is always honored when requested; the operator reports availability and a
        // resource with no configured provider simply yields no log frames (the channel stays silent).
        boolean wantLogs = requested.contains(LogStreamFrameType.LOG);
        boolean wantEvents = requested.contains(LogStreamFrameType.K8S_EVENT);

        try {
            Thread.ofVirtual().name("logs-stream-" + slug).start(() -> {
                try {
                    drainLoop(
                            emitter,
                            clusterName,
                            namespace,
                            slug,
                            pod,
                            container,
                            sinceEpochSeconds,
                            wantLogs,
                            wantEvents);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeQuietly(emitter, slug);
                    activeEmitters.remove(streamId);
                }
            });
        } catch (Throwable t) {
            activeEmitters.remove(streamId);
            emitter.completeWithError(t);
            log.warn("Failed to start logs-stream virtual thread for {}: {}", slug, t.getMessage());
        }
        return emitter;
    }

    private void drainLoop(
            SseEmitter emitter,
            String clusterName,
            String namespace,
            String slug,
            String pod,
            String container,
            long sinceEpochSeconds,
            boolean wantLogs,
            boolean wantEvents)
            throws InterruptedException {
        long logCursor = sinceEpochSeconds;
        // First drain delivers the buffered backlog (SPI contract: EPOCH for the first call) so a
        // stream opened against an already-running Resource still sees its recent lifecycle events;
        // the cursor then advances past the newest delivered event so live frames flow without
        // re-sending what the backlog already carried.
        Instant eventCursor = wantEvents ? Instant.EPOCH : Instant.now();
        long lastWriteAt = System.currentTimeMillis();

        while (true) {
            if (shuttingDown) {
                return;
            }
            boolean wrote = false;

            if (wantLogs) {
                List<LogStreamLogFrame> lines =
                        logStreamSupplier.tail(clusterName, namespace, slug, pod, container, logCursor, TAIL_BATCH);
                for (LogStreamLogFrame line : lines) {
                    if (!send(emitter, LogStreamFrameType.LOG.wireName(), line, slug)) {
                        return;
                    }
                    wrote = true;
                    long ts = epochSeconds(line.getTimestamp());
                    if (ts > logCursor) {
                        logCursor = ts;
                    }
                }
            }

            if (wantEvents) {
                List<LogStreamEventFrame> events =
                        k8sEventSupplier.drainSince(clusterName, namespace, slug, eventCursor);
                for (LogStreamEventFrame event : events) {
                    if (!send(emitter, LogStreamFrameType.K8S_EVENT.wireName(), event, slug)) {
                        return;
                    }
                    wrote = true;
                }
                eventCursor = k8sEventSupplier.latestArrival(clusterName, namespace, slug, eventCursor);
            }

            long now = System.currentTimeMillis();
            if (wrote) {
                lastWriteAt = now;
            } else if (now - lastWriteAt > KEEPALIVE_MS) {
                if (!send(emitter, KEEPALIVE_EVENT, "{}", slug)) {
                    return;
                }
                lastWriteAt = now;
            }
            TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MS);
        }
    }

    private boolean send(SseEmitter emitter, String event, Object data, String slug) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
            return true;
        } catch (IOException e) {
            log.debug("logs-stream client disconnected for {}: {}", slug, e.getMessage());
            return false;
        }
    }

    private static long epochSeconds(OffsetDateTime ts) {
        return ts != null ? ts.toEpochSecond() : 0L;
    }

    static OffsetDateTime toOffsetDateTime(double epochSeconds) {
        long millis = (long) (epochSeconds * 1000.0);
        return Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC);
    }

    private void completeQuietly(SseEmitter emitter, String slug) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("logs-stream emitter complete failed for {}: {}", slug, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdownActiveStreams() {
        shuttingDown = true;
        if (activeEmitters.isEmpty()) {
            return;
        }
        log.info("Completing {} active logs-stream emitter(s) on shutdown", activeEmitters.size());
        for (var emitter : activeEmitters.values()) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("logs-stream emitter complete on shutdown failed: {}", e.getMessage());
            }
        }
        activeEmitters.clear();
    }
}

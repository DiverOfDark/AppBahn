package eu.appbahn.platform.tunnel.rpc;

import com.github.f4b6a3.uuid.UuidCreator;
import eu.appbahn.platform.api.tunnel.AckCommandRequest;
import eu.appbahn.platform.api.tunnel.AdminConfigPush;
import eu.appbahn.platform.api.tunnel.GoodbyeAck;
import eu.appbahn.platform.api.tunnel.GoodbyeRequest;
import eu.appbahn.platform.api.tunnel.HelloAck;
import eu.appbahn.platform.api.tunnel.PushEventsAck;
import eu.appbahn.platform.api.tunnel.PushEventsRequest;
import eu.appbahn.platform.api.tunnel.QuotaRbacCachePush;
import eu.appbahn.platform.api.tunnel.RegisterClusterAck;
import eu.appbahn.platform.api.tunnel.RegisterClusterRequest;
import eu.appbahn.platform.api.tunnel.TunnelApi;
import eu.appbahn.platform.tunnel.auth.OperatorJwtVerifier;
import eu.appbahn.platform.tunnel.cluster.ClusterEntity;
import eu.appbahn.platform.tunnel.cluster.ClusterRepository;
import eu.appbahn.platform.tunnel.cluster.ClusterSessionRepository;
import eu.appbahn.platform.tunnel.cluster.ClusterSessionService;
import eu.appbahn.platform.tunnel.command.PendingCommandDispatcher;
import eu.appbahn.platform.tunnel.command.PendingCommandRepository;
import eu.appbahn.platform.tunnel.events.PushEventsHandler;
import eu.appbahn.platform.tunnel.push.AdminConfigSnapshotBuilder;
import eu.appbahn.platform.tunnel.push.QuotaRbacSnapshotBuilder;
import eu.appbahn.platform.tunnel.registration.ClusterRegistrationRequest;
import eu.appbahn.platform.tunnel.registration.ClusterRegistrationService;
import eu.appbahn.shared.tunnel.OperatorJwt;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST + SSE implementation of {@link TunnelApi}. Unary RPCs are plain JSON POSTs; the
 * {@code subscribeCommands} stream is Server-Sent Events with one event per frame.
 */
@RestController
@RequestMapping("/api")
public class OperatorTunnelController implements TunnelApi {

    private static final Logger log = LoggerFactory.getLogger(OperatorTunnelController.class);
    /** Keepalive frame cadence. Jetty idles connections out at 30s by default. */
    private static final long KEEPALIVE_MS = 15_000;
    /** How often the drain loop rebuilds snapshots to detect mutations from other replicas. */
    private static final long SNAPSHOT_REFRESH_MS = 2_000;
    /** How often to bump cluster.last_heartbeat_at from the open stream. */
    private static final long LIVENESS_REFRESH_MS = 10_000;
    /** Spring SSE timeout. The drain loop self-polices and closes cleanly on disconnect. */
    private static final long EMITTER_TIMEOUT_MS = Duration.ofHours(24).toMillis();

    private final OperatorJwtVerifier jwtVerifier;
    private final ClusterSessionRepository sessions;
    private final ClusterSessionService sessionService;
    private final ClusterRepository clusters;
    private final PendingCommandRepository pendingCommands;
    private final PendingCommandDispatcher commandDispatcher;
    private final PushEventsHandler pushEventsHandler;
    private final QuotaRbacSnapshotBuilder snapshotBuilder;
    private final AdminConfigSnapshotBuilder adminConfigBuilder;
    private final ClusterRegistrationService registrationService;
    private final String replicaId;

    public OperatorTunnelController(
            OperatorJwtVerifier jwtVerifier,
            ClusterSessionRepository sessions,
            ClusterSessionService sessionService,
            ClusterRepository clusters,
            PendingCommandRepository pendingCommands,
            PendingCommandDispatcher commandDispatcher,
            PushEventsHandler pushEventsHandler,
            QuotaRbacSnapshotBuilder snapshotBuilder,
            AdminConfigSnapshotBuilder adminConfigBuilder,
            ClusterRegistrationService registrationService,
            // Pod name in Kubernetes (always set by the Downward API). Falls back to a random
            // UUID so a misconfigured deployment still produces a unique ID per replica instead
            // of collapsing every replica's session-ownership check to the same key.
            @Value("${HOSTNAME:${random.uuid}}") String replicaId) {
        this.jwtVerifier = jwtVerifier;
        this.sessions = sessions;
        this.sessionService = sessionService;
        this.clusters = clusters;
        this.pendingCommands = pendingCommands;
        this.commandDispatcher = commandDispatcher;
        this.pushEventsHandler = pushEventsHandler;
        this.snapshotBuilder = snapshotBuilder;
        this.adminConfigBuilder = adminConfigBuilder;
        this.registrationService = registrationService;
        this.replicaId = replicaId;
    }

    @Override
    public ResponseEntity<RegisterClusterAck> registerCluster(RegisterClusterRequest request) {
        var response = registrationService.register(new ClusterRegistrationRequest(
                request.getClusterName(),
                request.getPublicKey(),
                request.getOperatorVersion(),
                request.getOperatorInstanceId() == null
                                || request.getOperatorInstanceId().isEmpty()
                        ? null
                        : request.getOperatorInstanceId()));
        var ack = new RegisterClusterAck();
        ack.setClusterName(response.clusterName());
        ack.setStatus(response.status().name());
        ack.setPublicKeyFingerprint(response.fingerprint());
        return ResponseEntity.ok(ack);
    }

    @Override
    public ResponseEntity<PushEventsAck> pushEvents(String authorization, PushEventsRequest request) {
        OperatorJwt jwt = authenticate(authorization);
        requireClusterMatch(jwt, request.getClusterName());
        int accepted = pushEventsHandler.handle(request);
        var ack = new PushEventsAck();
        ack.setAcceptedCount(accepted);
        return ResponseEntity.ok(ack);
    }

    @Override
    @Transactional
    public ResponseEntity<Void> ackCommand(String authorization, String correlationId, AckCommandRequest request) {
        OperatorJwt jwt = authenticate(authorization);
        UUID correlation;
        try {
            correlation = UUID.fromString(correlationId);
        } catch (IllegalArgumentException e) {
            throw TunnelApiException.invalidArgument("correlationId is not a valid UUID");
        }
        var cmd = pendingCommands
                .findByCorrelationId(correlation)
                .orElseThrow(() -> TunnelApiException.notFound("unknown correlationId: " + correlation));
        if (!cmd.getClusterName().equals(jwt.clusterName())) {
            throw TunnelApiException.permissionDenied("correlationId does not belong to this cluster");
        }
        cmd.setAckedAt(Instant.now());
        if (request.getResponse() != null) {
            cmd.setResponseStatus(
                    request.getResponse().getStatus() == null
                            ? null
                            : request.getResponse().getStatus().name());
            cmd.setResponseMessage(request.getResponse().getMessage());
        }
        pendingCommands.save(cmd);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<GoodbyeAck> goodbye(String authorization, GoodbyeRequest request) {
        OperatorJwt jwt = authenticate(authorization);
        requireClusterMatch(jwt, request.getClusterName());
        sessions.deleteById(jwt.clusterName());
        return ResponseEntity.ok(new GoodbyeAck());
    }

    @Override
    public SseEmitter subscribeCommands(
            String authorization,
            String clusterName,
            String operatorInstanceId,
            String operatorVersion,
            long lastAdminConfigRevision,
            long lastQuotaRbacRevision) {
        OperatorJwt jwt = authenticate(authorization);
        if (!jwt.clusterName().equals(clusterName)) {
            throw TunnelApiException.invalidArgument("clusterName does not match JWT iss");
        }

        UUID sessionId = UuidCreator.getTimeOrderedEpoch();
        sessionService.claimSession(jwt.clusterName(), sessionId, replicaId, operatorInstanceId);

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        QuotaRbacCachePush initialQuota = snapshotBuilder.buildFor(clusterName);
        AdminConfigPush initialAdmin = adminConfigBuilder.build();

        // Send the HelloAck synchronously on the request thread so clients see it before
        // the handler returns. Any subsequent frames come from the drain-loop thread.
        try {
            var hello = new HelloAck();
            hello.setSessionId(sessionId.toString());
            if (initialQuota.getRevision() != lastQuotaRbacRevision) {
                hello.setQuotaRbac(initialQuota);
            }
            if (initialAdmin.getRevision() != lastAdminConfigRevision) {
                hello.setAdminConfig(initialAdmin);
            }
            emitter.send(SseEmitter.event().name(HelloAck.EVENT_NAME).data(hello));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        Thread.ofVirtual().name("tunnel-drain-" + clusterName).start(() -> {
            try {
                drainCommandLoop(
                        emitter, clusterName, sessionId, initialQuota.getRevision(), initialAdmin.getRevision());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                emitter.complete();
            }
        });
        emitter.onCompletion(() -> log.debug("SSE emitter completed for cluster={}", clusterName));
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.debug("SSE emitter error cluster={}: {}", clusterName, e.getMessage()));
        return emitter;
    }

    /**
     * Drains {@code pending_command} rows for this cluster onto the open SSE stream. Exits
     * when a write fails (client disconnected) or the session row is no longer ours (a newer
     * subscription took over). Polling interval is 500ms — LISTEN/NOTIFY can be layered on
     * later if queue depth justifies it.
     *
     * <p>Snapshot pushes ({@code quota-rbac-cache-push}, {@code admin-config-push}) are
     * revision-gated: the builders hash their content and this loop only emits when the
     * current hash differs from the last sent. A periodic re-check is still needed because
     * there's no LISTEN/NOTIFY yet — a mutation on a different replica must be picked up by
     * the subscribing replica's next poll.
     */
    private void drainCommandLoop(
            SseEmitter emitter,
            String clusterName,
            UUID sessionId,
            long initialQuotaRbacRevision,
            long initialAdminConfigRevision)
            throws InterruptedException {
        long now = System.currentTimeMillis();
        long lastWriteAt = now;
        long lastSnapshotCheckAt = now;
        long lastLivenessAt = 0L;
        long iterations = 0;
        long lastSentQuotaRbacRevision = initialQuotaRbacRevision;
        long lastSentAdminConfigRevision = initialAdminConfigRevision;
        Instant lastObservedCacheMissAt = Instant.EPOCH;
        log.info("drain loop started for cluster={} session={}", clusterName, sessionId);
        sessionService.touchSession(clusterName);
        while (true) {
            if (!sessionIsStillOurs(clusterName, sessionId)) {
                log.info("drain loop exiting — session no longer ours cluster={}", clusterName);
                return;
            }
            iterations++;
            if (iterations % 40 == 0) {
                log.info("drain loop alive iter={} cluster={}", iterations, clusterName);
            }
            if (System.currentTimeMillis() - lastLivenessAt > LIVENESS_REFRESH_MS) {
                sessionService.touchSession(clusterName);
                lastLivenessAt = System.currentTimeMillis();
            }
            var batch = commandDispatcher.pollBatch(clusterName, replicaId, 50);
            if (!batch.isEmpty()) {
                // Admission webhook is fail-closed on unknown namespaces. If an env mutation
                // landed since our last check, push the fresh snapshot BEFORE the commands so
                // the operator's admission cache sees it first.
                long emitted = maybePushQuotaRbac(emitter, clusterName, lastSentQuotaRbacRevision);
                if (emitted != lastSentQuotaRbacRevision) {
                    lastSentQuotaRbacRevision = emitted;
                    lastWriteAt = System.currentTimeMillis();
                }
            }
            for (var claimed : batch) {
                try {
                    emitter.send(SseEmitter.event().name(claimed.eventType()).data(claimed.body()));
                    commandDispatcher.markDelivered(claimed.commandId());
                    lastWriteAt = System.currentTimeMillis();
                } catch (IOException e) {
                    log.info("client disconnected cluster={}: {}", clusterName, e.getMessage());
                    return;
                }
            }
            if (System.currentTimeMillis() - lastSnapshotCheckAt > SNAPSHOT_REFRESH_MS) {
                lastSnapshotCheckAt = System.currentTimeMillis();
                // Operator reported an admission-cache miss since our last check? Force the
                // next maybePushQuotaRbac to emit regardless of revision match — resetting the
                // tracker to a sentinel no content-hash can equal.
                Instant missAt = clusters.findById(clusterName)
                        .map(ClusterEntity::getLastAdmissionMissAt)
                        .orElse(null);
                if (missAt != null && missAt.isAfter(lastObservedCacheMissAt)) {
                    lastObservedCacheMissAt = missAt;
                    lastSentQuotaRbacRevision = -1L;
                }
                long emittedQuota = maybePushQuotaRbac(emitter, clusterName, lastSentQuotaRbacRevision);
                if (emittedQuota != lastSentQuotaRbacRevision) {
                    lastSentQuotaRbacRevision = emittedQuota;
                    lastWriteAt = System.currentTimeMillis();
                }
                long emittedAdmin = maybePushAdminConfig(emitter, lastSentAdminConfigRevision);
                if (emittedAdmin != lastSentAdminConfigRevision) {
                    lastSentAdminConfigRevision = emittedAdmin;
                    lastWriteAt = System.currentTimeMillis();
                }
            }
            if (batch.isEmpty()) {
                if (System.currentTimeMillis() - lastWriteAt > KEEPALIVE_MS) {
                    try {
                        emitter.send(SseEmitter.event()
                                .name(TunnelApi.KEEPALIVE_EVENT)
                                .data("{}"));
                        lastWriteAt = System.currentTimeMillis();
                    } catch (IOException e) {
                        log.info("keepalive write failed cluster={}: {}", clusterName, e.getMessage());
                        return;
                    }
                }
                TimeUnit.MILLISECONDS.sleep(500);
            }
        }
    }

    private long maybePushQuotaRbac(SseEmitter emitter, String clusterName, long lastSentRevision) {
        QuotaRbacCachePush snap = snapshotBuilder.buildFor(clusterName);
        if (snap.getRevision() == lastSentRevision) {
            return lastSentRevision;
        }
        try {
            emitter.send(SseEmitter.event().name(QuotaRbacCachePush.EVENT_NAME).data(snap));
        } catch (IOException e) {
            return lastSentRevision;
        }
        return snap.getRevision();
    }

    private long maybePushAdminConfig(SseEmitter emitter, long lastSentRevision) {
        AdminConfigPush cfg = adminConfigBuilder.build();
        if (cfg.getRevision() == lastSentRevision) {
            return lastSentRevision;
        }
        try {
            emitter.send(SseEmitter.event().name(AdminConfigPush.EVENT_NAME).data(cfg));
        } catch (IOException e) {
            return lastSentRevision;
        }
        return cfg.getRevision();
    }

    private boolean sessionIsStillOurs(String clusterName, UUID sessionId) {
        return sessions.findById(clusterName)
                .map(s -> s.getSessionId().equals(sessionId)
                        && s.getSubscribingReplicaId().equals(replicaId))
                .orElse(false);
    }

    // -----------------------------------------------------------------------
    // Auth
    // -----------------------------------------------------------------------

    private OperatorJwt authenticate(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw TunnelApiException.unauthenticated("missing bearer token");
        }
        return jwtVerifier.verify(authHeader.substring(7));
    }

    private void requireClusterMatch(OperatorJwt jwt, String clusterName) {
        if (!jwt.clusterName().equals(clusterName)) {
            throw TunnelApiException.invalidArgument("clusterName does not match JWT iss");
        }
    }
}

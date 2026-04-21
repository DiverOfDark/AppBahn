package eu.appbahn.platform.tunnel.rpc;

import com.github.f4b6a3.uuid.UuidCreator;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
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
import eu.appbahn.tunnel.v1.AckCommandRequest;
import eu.appbahn.tunnel.v1.CommandResponse;
import eu.appbahn.tunnel.v1.GoodbyeAck;
import eu.appbahn.tunnel.v1.GoodbyeRequest;
import eu.appbahn.tunnel.v1.HelloAck;
import eu.appbahn.tunnel.v1.PlatformMessage;
import eu.appbahn.tunnel.v1.PushEventsAck;
import eu.appbahn.tunnel.v1.PushEventsRequest;
import eu.appbahn.tunnel.v1.RegisterClusterAck;
import eu.appbahn.tunnel.v1.RegisterClusterRequest;
import eu.appbahn.tunnel.v1.SubscribeCommandsRequest;
import eu.appbahn.tunnel.wire.Envelope;
import eu.appbahn.tunnel.wire.EnvelopeFlags;
import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connect-protocol tunnel endpoints. Every RPC accepts {@code application/json}
 * (Connect JSON wire format) and returns JSON; {@code application/proto} is a
 * future extension. {@code SubscribeCommands} is the only server-stream; the other
 * four RPCs are unary.
 */
@RestController
@RequestMapping("/appbahn.tunnel.v1.OperatorTunnel")
public class OperatorTunnelController {

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
    private final JsonFormat.Parser jsonParser = JsonFormat.parser().ignoringUnknownFields();
    private final JsonFormat.Printer jsonPrinter = JsonFormat.printer().omittingInsignificantWhitespace();

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
            // UUID so a misconfigured deployment still produces a unique ID per replica
            // instead of collapsing every replica's session-ownership check to the same key.
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

    /**
     * The only RPC the operator can call without a JWT — the platform doesn't yet have
     * the operator's public key, so it can't verify one. Idempotent: operator sends this
     * on every startup; platform UPSERTs the cluster row and re-runs auto-approval.
     */
    @PostMapping(
            value = "/RegisterCluster",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> registerCluster(@RequestBody String body) throws InvalidProtocolBufferException {
        RegisterClusterRequest req =
                parse(body, RegisterClusterRequest.newBuilder()).build();
        var response = registrationService.register(new ClusterRegistrationRequest(
                req.getClusterName(),
                req.getPublicKey(),
                req.getOperatorVersion(),
                req.getOperatorInstanceId().isEmpty() ? null : req.getOperatorInstanceId()));
        RegisterClusterAck ack = RegisterClusterAck.newBuilder()
                .setClusterName(response.clusterName())
                .setStatus(response.status().name())
                .setPublicKeyFingerprint(response.fingerprint())
                .build();
        return json(ack);
    }

    @PostMapping(
            value = "/PushEvents",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> pushEvents(
            @RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody String body)
            throws InvalidProtocolBufferException {
        OperatorJwt jwt = authenticate(authorization);
        PushEventsRequest req = parse(body, PushEventsRequest.newBuilder()).build();
        requireClusterMatch(jwt, req.getClusterName());
        int accepted = pushEventsHandler.handle(req);
        PushEventsAck ack =
                PushEventsAck.newBuilder().setAcceptedCount(accepted).build();
        return json(ack);
    }

    @PostMapping(
            value = "/AckCommand",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<String> ackCommand(
            @RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody String body)
            throws InvalidProtocolBufferException {
        OperatorJwt jwt = authenticate(authorization);
        AckCommandRequest req = parse(body, AckCommandRequest.newBuilder()).build();
        // JWT authoritative for cluster scope; correlation_id lookup narrows further.
        java.util.UUID correlation;
        try {
            correlation = java.util.UUID.fromString(req.getCorrelationId());
        } catch (IllegalArgumentException e) {
            throw TunnelConnectException.invalidArgument("correlation_id is not a valid UUID");
        }
        var cmd = pendingCommands
                .findByCorrelationId(correlation)
                .orElseThrow(() -> TunnelConnectException.notFound("unknown correlation_id: " + correlation));
        if (!cmd.getClusterName().equals(jwt.clusterName())) {
            throw TunnelConnectException.permissionDenied("correlation_id does not belong to this cluster");
        }
        cmd.setAckedAt(Instant.now());
        CommandResponse response = req.getResponse();
        cmd.setResponseStatus(response.getStatus().name());
        cmd.setResponseMessage(response.getMessage());
        pendingCommands.save(cmd);
        return json(Empty.getDefaultInstance());
    }

    @PostMapping(
            value = "/Goodbye",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> goodbye(
            @RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody String body)
            throws InvalidProtocolBufferException {
        OperatorJwt jwt = authenticate(authorization);
        GoodbyeRequest req = parse(body, GoodbyeRequest.newBuilder()).build();
        requireClusterMatch(jwt, req.getClusterName());
        sessions.deleteById(jwt.clusterName());
        return json(GoodbyeAck.getDefaultInstance());
    }

    @PostMapping(value = "/SubscribeCommands", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void subscribeCommands(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody String body,
            HttpServletResponse servletResponse)
            throws InvalidProtocolBufferException, java.io.IOException {
        OperatorJwt jwt = authenticate(authorization);
        SubscribeCommandsRequest req =
                parse(body, SubscribeCommandsRequest.newBuilder()).build();
        requireClusterMatch(jwt, req.getClusterName());

        java.util.UUID sessionId = UuidCreator.getTimeOrderedEpoch();
        sessionService.claimSession(jwt.clusterName(), sessionId, replicaId, req);

        String clusterName = jwt.clusterName();
        eu.appbahn.tunnel.v1.QuotaRbacCachePush initialSnapshot = snapshotBuilder.buildFor(clusterName);
        eu.appbahn.tunnel.v1.AdminConfigPush initialAdminConfig = adminConfigBuilder.build();

        servletResponse.setStatus(HttpServletResponse.SC_OK);
        servletResponse.setContentType("application/connect+json");
        servletResponse.setBufferSize(0);
        servletResponse.flushBuffer();
        OutputStream out = servletResponse.getOutputStream();
        try {
            HelloAck.Builder hello = HelloAck.newBuilder().setSessionId(sessionId.toString());
            // Operator echoes its last-applied revisions on reconnect. Only include
            // the snapshot bodies in HelloAck if the platform has something newer;
            // otherwise send an empty oneof slot so the operator doesn't re-apply
            // what it already has.
            if (initialSnapshot.getRevision() != req.getLastQuotaRbacRevision()) {
                hello.setQuotaRbac(initialSnapshot);
            }
            if (initialAdminConfig.getRevision() != req.getLastAdminConfigRevision()) {
                hello.setAdminConfig(initialAdminConfig);
            }
            PlatformMessage frame =
                    PlatformMessage.newBuilder().setHelloAck(hello.build()).build();
            writeFrame(out, frame);
            servletResponse.flushBuffer();
            drainCommandLoop(
                    out,
                    servletResponse,
                    clusterName,
                    sessionId,
                    initialSnapshot.getRevision(),
                    initialAdminConfig.getRevision());
            writeEndStream(out);
            servletResponse.flushBuffer();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.io.IOException ignored) {
            // Client disconnected — normal on operator restart / network blip.
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(OperatorTunnelController.class)
                    .warn("SubscribeCommands drain loop failed: {}", e.getMessage(), e);
            try {
                writeEndStream(out);
                servletResponse.flushBuffer();
            } catch (java.io.IOException ignored) {
                // already broken
            }
        }
    }

    /**
     * Drains {@code pending_command} rows for this cluster onto the open stream. The loop
     * exits when a write fails (client disconnected) or the session row is no longer ours
     * (a newer subscription took over). Polling interval is 500ms — LISTEN/NOTIFY can be
     * layered on later if the queue depth justifies it.
     *
     * <p>Snapshot pushes ({@code QuotaRbacCachePush}, {@code AdminConfigPush}) are
     * revision-gated: the builders hash their content and the loop only emits when the
     * current hash differs from the last value we sent on this stream. A fixed periodic
     * re-check is still needed because we don't have {@code LISTEN}/{@code NOTIFY} yet —
     * a mutation that lands on a different replica must be picked up by the subscribing
     * replica's next poll. With content-addressed revisions the cost of a no-op tick is
     * one DB read and zero bytes on the wire.
     *
     * <p>Sends an empty {@link PlatformMessage} every {@value #KEEPALIVE_MS} ms so the
     * connection doesn't idle out at Jetty's 30s default. Operator ignores no-op frames
     * (oneof is {@code MESSAGE_NOT_SET}).
     */
    private static final long KEEPALIVE_MS = 15_000;

    /** How often the drain loop rebuilds snapshots to detect mutations from other replicas. */
    private static final long SNAPSHOT_REFRESH_MS = 2_000;

    /** How often to bump cluster.last_heartbeat_at from the open SubscribeCommands stream.
     * The stream is the only liveness signal now (operator-side HeartbeatScheduler is gone). */
    private static final long LIVENESS_REFRESH_MS = 10_000;

    private static final org.slf4j.Logger DRAIN_LOG = org.slf4j.LoggerFactory.getLogger("drainCommandLoop");

    private void drainCommandLoop(
            OutputStream out,
            HttpServletResponse response,
            String clusterName,
            java.util.UUID sessionId,
            long initialQuotaRbacRevision,
            long initialAdminConfigRevision)
            throws java.io.IOException, InterruptedException {
        long now = System.currentTimeMillis();
        long lastWriteAt = now;
        long lastSnapshotCheckAt = now;
        long lastLivenessAt = 0L;
        long iterations = 0;
        long lastSentQuotaRbacRevision = initialQuotaRbacRevision;
        long lastSentAdminConfigRevision = initialAdminConfigRevision;
        Instant lastObservedCacheMissAt = Instant.EPOCH;
        DRAIN_LOG.info("drain loop started for cluster={} session={}", clusterName, sessionId);
        sessionService.touchSession(clusterName);
        while (true) {
            boolean stillOurs = sessionIsStillOurs(clusterName, sessionId);
            if (!stillOurs) {
                DRAIN_LOG.info("drain loop exiting — session no longer ours cluster={}", clusterName);
                return;
            }
            iterations++;
            if (iterations % 40 == 0) {
                DRAIN_LOG.info("drain loop alive iter={} cluster={}", iterations, clusterName);
            }
            if (System.currentTimeMillis() - lastLivenessAt > LIVENESS_REFRESH_MS) {
                sessionService.touchSession(clusterName);
                lastLivenessAt = System.currentTimeMillis();
            }
            List<PendingCommandDispatcher.Claimed> batch = commandDispatcher.pollBatch(clusterName, replicaId, 50);
            if (!batch.isEmpty()) {
                // Admission webhook is fail-closed on unknown namespaces. If an env
                // mutation landed since our last check, push the fresh snapshot BEFORE
                // the commands so the operator's admission cache sees it first.
                long emitted = maybePushQuotaRbac(out, response, clusterName, lastSentQuotaRbacRevision);
                if (emitted != lastSentQuotaRbacRevision) {
                    lastSentQuotaRbacRevision = emitted;
                    lastWriteAt = System.currentTimeMillis();
                }
            }
            for (var claimed : batch) {
                DRAIN_LOG.info("drain writing frame cluster={} corrId={}", clusterName, claimed.correlationId());
                writeFrame(out, claimed.frame());
                response.flushBuffer();
                commandDispatcher.markDelivered(claimed.commandId());
                lastWriteAt = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - lastSnapshotCheckAt > SNAPSHOT_REFRESH_MS) {
                lastSnapshotCheckAt = System.currentTimeMillis();
                // Operator reported an admission-cache miss since our last check? Force the
                // next maybePushQuotaRbac to emit regardless of revision match — resetting
                // the tracker to a sentinel no content-hash can equal.
                Instant missAt = clusters.findById(clusterName)
                        .map(ClusterEntity::getLastAdmissionMissAt)
                        .orElse(null);
                if (missAt != null && missAt.isAfter(lastObservedCacheMissAt)) {
                    lastObservedCacheMissAt = missAt;
                    lastSentQuotaRbacRevision = -1L;
                }
                long emittedQuota = maybePushQuotaRbac(out, response, clusterName, lastSentQuotaRbacRevision);
                if (emittedQuota != lastSentQuotaRbacRevision) {
                    lastSentQuotaRbacRevision = emittedQuota;
                    lastWriteAt = System.currentTimeMillis();
                }
                long emittedAdmin = maybePushAdminConfig(out, response, lastSentAdminConfigRevision);
                if (emittedAdmin != lastSentAdminConfigRevision) {
                    lastSentAdminConfigRevision = emittedAdmin;
                    lastWriteAt = System.currentTimeMillis();
                }
            }
            if (batch.isEmpty()) {
                if (System.currentTimeMillis() - lastWriteAt > KEEPALIVE_MS) {
                    writeFrame(out, PlatformMessage.getDefaultInstance());
                    response.flushBuffer();
                    lastWriteAt = System.currentTimeMillis();
                }
                TimeUnit.MILLISECONDS.sleep(500);
            }
        }
    }

    /**
     * Build the current {@code QuotaRbacCachePush} and emit it only if its content-hash
     * revision differs from what the operator last received on this stream. Returns the
     * revision the caller should track as "last sent" (unchanged if nothing was emitted).
     */
    private long maybePushQuotaRbac(
            OutputStream out, HttpServletResponse response, String clusterName, long lastSentRevision)
            throws java.io.IOException {
        eu.appbahn.tunnel.v1.QuotaRbacCachePush snap = snapshotBuilder.buildFor(clusterName);
        if (snap.getRevision() == lastSentRevision) {
            return lastSentRevision;
        }
        writeFrame(out, PlatformMessage.newBuilder().setQuotaRbacCachePush(snap).build());
        response.flushBuffer();
        return snap.getRevision();
    }

    private long maybePushAdminConfig(OutputStream out, HttpServletResponse response, long lastSentRevision)
            throws java.io.IOException {
        eu.appbahn.tunnel.v1.AdminConfigPush cfg = adminConfigBuilder.build();
        if (cfg.getRevision() == lastSentRevision) {
            return lastSentRevision;
        }
        writeFrame(out, PlatformMessage.newBuilder().setAdminConfigPush(cfg).build());
        response.flushBuffer();
        return cfg.getRevision();
    }

    private boolean sessionIsStillOurs(String clusterName, java.util.UUID sessionId) {
        return sessions.findById(clusterName)
                .map(s -> s.getSessionId().equals(sessionId)
                        && s.getSubscribingReplicaId().equals(replicaId))
                .orElse(false);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private OperatorJwt authenticate(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw TunnelConnectException.unauthenticated("missing bearer token");
        }
        return jwtVerifier.verify(authHeader.substring(7));
    }

    private void requireClusterMatch(OperatorJwt jwt, String clusterName) {
        if (!jwt.clusterName().equals(clusterName)) {
            throw TunnelConnectException.invalidArgument("cluster_name does not match JWT iss");
        }
    }

    private <B extends Message.Builder> B parse(String json, B builder) {
        try {
            jsonParser.merge(json, builder);
            return builder;
        } catch (InvalidProtocolBufferException e) {
            throw TunnelConnectException.invalidArgument("malformed JSON body: " + e.getMessage());
        }
    }

    private ResponseEntity<String> json(Message msg) throws InvalidProtocolBufferException {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(jsonPrinter.print(msg));
    }

    private void writeFrame(OutputStream out, Message msg) throws java.io.IOException {
        byte[] json = jsonPrinter.print(msg).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Envelope.write(out, (byte) 0, json);
        out.flush();
    }

    private void writeEndStream(OutputStream out) throws java.io.IOException {
        Envelope.write(out, EnvelopeFlags.END_STREAM, "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.flush();
    }
}

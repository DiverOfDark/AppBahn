package eu.appbahn.platform.tunnel.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.tunnel.AdmissionApproved;
import eu.appbahn.platform.api.tunnel.AdmissionCacheMissReport;
import eu.appbahn.platform.api.tunnel.AuditLogEvent;
import eu.appbahn.platform.api.tunnel.FullResourceSyncChunk;
import eu.appbahn.platform.api.tunnel.OperatorEvent;
import eu.appbahn.platform.api.tunnel.PushEventsRequest;
import eu.appbahn.platform.api.tunnel.ResourceDeletedBatch;
import eu.appbahn.platform.api.tunnel.ResourceSyncBatch;
import eu.appbahn.platform.resource.service.ResourceSyncService;
import eu.appbahn.platform.tunnel.cluster.ClusterRepository;
import eu.appbahn.platform.tunnel.command.FullSyncChunkBufferEntity;
import eu.appbahn.platform.tunnel.command.FullSyncChunkBufferRepository;
import eu.appbahn.shared.tunnel.FullSyncPayload;
import eu.appbahn.shared.tunnel.ResourceSyncPayload;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatches operator-emitted events from {@code PushEvents} to the existing
 * {@link ResourceSyncService}. Business logic (upsert/delete/full-sync) is unchanged — only
 * the transport moved. {@link FullResourceSyncChunk} events are buffered so that chunks
 * landing on different platform replicas still converge into one atomic set-diff commit on
 * the {@code complete=true} chunk.
 */
@Service
public class PushEventsHandler {

    private static final Logger log = LoggerFactory.getLogger(PushEventsHandler.class);

    private final ResourceSyncService resourceSyncService;
    private final TunnelEventMapper eventMapper;
    private final FullSyncChunkBufferRepository chunkBuffer;
    private final ClusterRepository clusterRepository;
    private final AuditLogWriterService auditLogWriter;
    private final ObjectMapper jsonMapper;

    public PushEventsHandler(
            ResourceSyncService resourceSyncService,
            TunnelEventMapper eventMapper,
            FullSyncChunkBufferRepository chunkBuffer,
            ClusterRepository clusterRepository,
            AuditLogWriterService auditLogWriter,
            ObjectMapper jsonMapper) {
        this.resourceSyncService = resourceSyncService;
        this.eventMapper = eventMapper;
        this.chunkBuffer = chunkBuffer;
        this.clusterRepository = clusterRepository;
        this.auditLogWriter = auditLogWriter;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public int handle(PushEventsRequest request) {
        // One tx per PushEvents call: chunk-buffer deletes on full-sync commit and the
        // admission-miss cluster stamp both need a surrounding transaction, and cross-event
        // atomicity matches the prior intent — a failed batch is the operator's problem to
        // retry, not silently half-applied.
        for (OperatorEvent event : request.getEvents()) {
            dispatch(request.getClusterName(), event);
        }
        return request.getEvents().size();
    }

    private void dispatch(String clusterName, OperatorEvent event) {
        switch (event) {
            case ResourceSyncBatch b -> handleSyncBatch(clusterName, b);
            case ResourceDeletedBatch b -> handleDeletedBatch(b);
            case FullResourceSyncChunk c -> handleFullSyncChunk(clusterName, c);
            case AdmissionApproved a -> {
                // Treat an AdmissionApproved exactly like a sync — it's a fast-path pre-seed
                // that carries the same payload shape.
                if (a.getItem() != null) {
                    resourceSyncService.syncResource(eventMapper.toPayload(a.getItem(), clusterName));
                }
            }
            case AdmissionCacheMissReport r -> handleAdmissionCacheMiss(clusterName, r);
            case AuditLogEvent e -> auditLogWriter.writeFromOperator(e);
            default -> log.debug("Unhandled OperatorEvent type: {}", event.getType());
        }
    }

    /**
     * Stamp {@code cluster.last_admission_miss_at} so the subscribing replica's drain loop
     * force-pushes a fresh {@code QuotaRbacCachePush} even if the snapshot revision hasn't
     * changed. Cross-replica safe: the drain loop polls this column regardless of which
     * replica handled the report.
     */
    private void handleAdmissionCacheMiss(String clusterName, AdmissionCacheMissReport report) {
        log.info(
                "Admission cache miss from cluster={} ns={} user={} reason={}",
                clusterName,
                report.getNamespace(),
                report.getUserOidcSubject(),
                report.getReason());
        clusterRepository.findById(clusterName).ifPresent(cluster -> {
            cluster.setLastAdmissionMissAt(Instant.now());
            clusterRepository.save(cluster);
        });
    }

    private void handleSyncBatch(String clusterName, ResourceSyncBatch batch) {
        if (batch.getItems() == null) return;
        batch.getItems().forEach(item -> resourceSyncService.syncResource(eventMapper.toPayload(item, clusterName)));
    }

    private void handleDeletedBatch(ResourceDeletedBatch batch) {
        if (batch.getResourceSlugs() == null) return;
        batch.getResourceSlugs().forEach(resourceSyncService::deleteResourceSync);
    }

    private void handleFullSyncChunk(String clusterName, FullResourceSyncChunk chunk) {
        UUID sessionId;
        try {
            sessionId = UUID.fromString(chunk.getSyncSessionId());
        } catch (IllegalArgumentException e) {
            log.warn("Full-sync chunk dropped: malformed syncSessionId {}", chunk.getSyncSessionId());
            return;
        }
        byte[] payload;
        try {
            payload = jsonMapper.writeValueAsBytes(chunk);
        } catch (JsonProcessingException e) {
            log.warn("Full-sync chunk {} could not be re-serialised: {}", chunk.getChunkIndex(), e.getMessage());
            return;
        }

        var entity = new FullSyncChunkBufferEntity();
        entity.setId(new FullSyncChunkBufferEntity.Pk(clusterName, sessionId, chunk.getChunkIndex()));
        entity.setPayload(payload);
        entity.setReceivedAt(Instant.now());
        chunkBuffer.save(entity);

        if (!chunk.isComplete()) {
            return;
        }
        commitFullSync(clusterName, sessionId);
    }

    private void commitFullSync(String clusterName, UUID sessionId) {
        var allChunks = chunkBuffer.findByIdClusterNameAndIdSyncSessionIdOrderByIdChunkIndex(clusterName, sessionId);
        List<ResourceSyncPayload> resources = new ArrayList<>();
        for (var entity : allChunks) {
            try {
                FullResourceSyncChunk chunk = jsonMapper.readValue(
                        new String(entity.getPayload(), StandardCharsets.UTF_8), FullResourceSyncChunk.class);
                if (chunk.getItems() != null) {
                    chunk.getItems().forEach(item -> resources.add(eventMapper.toPayload(item, clusterName)));
                }
            } catch (Exception e) {
                log.warn(
                        "Full-sync chunk {} for session {} had unparseable payload — skipping",
                        entity.getId().getChunkIndex(),
                        sessionId);
            }
        }
        resourceSyncService.fullSync(new FullSyncPayload(clusterName, resources));
        chunkBuffer.deleteByIdClusterNameAndIdSyncSessionId(clusterName, sessionId);
        log.info("Committed full sync {} for cluster {}: {} resources", sessionId, clusterName, resources.size());
    }
}

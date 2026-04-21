package eu.appbahn.platform.tunnel.events;

import eu.appbahn.platform.resource.service.ResourceSyncService;
import eu.appbahn.platform.tunnel.cluster.ClusterRepository;
import eu.appbahn.platform.tunnel.command.FullSyncChunkBufferEntity;
import eu.appbahn.platform.tunnel.command.FullSyncChunkBufferRepository;
import eu.appbahn.tunnel.v1.AdmissionCacheMissReport;
import eu.appbahn.tunnel.v1.FullResourceSyncChunk;
import eu.appbahn.tunnel.v1.OperatorEvent;
import eu.appbahn.tunnel.v1.PushEventsRequest;
import eu.appbahn.tunnel.v1.ResourceDeletedBatch;
import eu.appbahn.tunnel.v1.ResourceSyncBatch;
import eu.appbahn.tunnel.v1.ResourceSyncItem;
import eu.appbahn.tunnel.wire.FullSyncPayload;
import eu.appbahn.tunnel.wire.ResourceSyncPayload;
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
 * {@link ResourceSyncService} (the upsert/delete/full-sync business logic is unchanged;
 * only the transport moved).
 * <p>{@link FullResourceSyncChunk} events are accumulated in {@link FullSyncChunkBufferEntity}
 * so that chunks landing on different platform replicas still converge into one
 * atomic set-diff commit on the {@code complete=true} chunk.
 */
@Service
public class PushEventsHandler {

    private static final Logger log = LoggerFactory.getLogger(PushEventsHandler.class);

    private final ResourceSyncService resourceSyncService;
    private final TunnelEventMapper mapper;
    private final FullSyncChunkBufferRepository chunkBuffer;
    private final ClusterRepository clusterRepository;

    public PushEventsHandler(
            ResourceSyncService resourceSyncService,
            TunnelEventMapper mapper,
            FullSyncChunkBufferRepository chunkBuffer,
            ClusterRepository clusterRepository) {
        this.resourceSyncService = resourceSyncService;
        this.mapper = mapper;
        this.chunkBuffer = chunkBuffer;
        this.clusterRepository = clusterRepository;
    }

    @Transactional
    public int handle(PushEventsRequest request) {
        // One tx per PushEvents call: chunk-buffer deletes on full-sync commit and the
        // admission-miss cluster stamp both need a surrounding transaction (derived delete
        // queries aren't auto-wrapped, and cross-event atomicity matches 6056fa3's intent
        // that a failed batch is the operator's problem to retry, not silently half-applied).
        for (OperatorEvent event : request.getEventsList()) {
            dispatch(request.getClusterName(), event);
        }
        return request.getEventsCount();
    }

    private void dispatch(String clusterName, OperatorEvent event) {
        switch (event.getEventCase()) {
            case RESOURCE_SYNC_BATCH -> handleSyncBatch(clusterName, event.getResourceSyncBatch());
            case RESOURCE_DELETED_BATCH -> handleDeletedBatch(event.getResourceDeletedBatch());
            case FULL_RESOURCE_SYNC_CHUNK -> handleFullSyncChunk(clusterName, event.getFullResourceSyncChunk());
            case ADMISSION_APPROVED -> {
                // Treat an AdmissionApproved exactly like a sync — it's a fast-path pre-seed
                // that carries the same payload shape.
                ResourceSyncItem item = event.getAdmissionApproved().getItem();
                resourceSyncService.syncResource(mapper.toPayload(item, clusterName));
            }
            case ADMISSION_CACHE_MISS_REPORT ->
                handleAdmissionCacheMiss(clusterName, event.getAdmissionCacheMissReport());
            case RESOURCE_TYPE_SYNC_CHUNK, DEPLOYMENT_STATUS_UPDATE, EVENT_NOT_SET -> {
                // Handlers land in a follow-up — resource-type sync and deployment-status
                // updates feed features (CRD discovery, deploy pipelines) that this sprint
                // doesn't own yet. Logged at debug so operator-side emission can be verified.
                log.debug("Unhandled OperatorEvent case: {}", event.getEventCase());
            }
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
        for (ResourceSyncItem item : batch.getItemsList()) {
            resourceSyncService.syncResource(mapper.toPayload(item, clusterName));
        }
    }

    private void handleDeletedBatch(ResourceDeletedBatch batch) {
        for (String slug : batch.getResourceSlugsList()) {
            resourceSyncService.deleteResourceSync(slug);
        }
    }

    private void handleFullSyncChunk(String clusterName, FullResourceSyncChunk chunk) {
        UUID sessionId;
        try {
            sessionId = UUID.fromString(chunk.getSyncSessionId());
        } catch (IllegalArgumentException e) {
            log.warn("Full-sync chunk dropped: malformed sync_session_id {}", chunk.getSyncSessionId());
            return;
        }
        byte[] payload = chunk.toByteArray();

        var entity = new FullSyncChunkBufferEntity();
        entity.setId(new FullSyncChunkBufferEntity.Pk(clusterName, sessionId, (int) chunk.getChunkIndex()));
        entity.setPayload(payload);
        entity.setReceivedAt(Instant.now());
        chunkBuffer.save(entity);

        if (!chunk.getComplete()) {
            return;
        }
        commitFullSync(clusterName, sessionId);
    }

    private void commitFullSync(String clusterName, UUID sessionId) {
        var allChunks = chunkBuffer.findByIdClusterNameAndIdSyncSessionIdOrderByIdChunkIndex(clusterName, sessionId);
        List<ResourceSyncPayload> resources = new ArrayList<>();
        for (var entity : allChunks) {
            try {
                FullResourceSyncChunk chunk = FullResourceSyncChunk.parseFrom(entity.getPayload());
                for (ResourceSyncItem item : chunk.getItemsList()) {
                    resources.add(mapper.toPayload(item, clusterName));
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

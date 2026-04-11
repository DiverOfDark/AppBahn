package eu.appbahn.platform.resource.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.internal.model.FullResourceSyncRequest;
import eu.appbahn.platform.api.internal.model.ResourceSyncRequest;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.resource.entity.ResourceCacheEntity;
import eu.appbahn.platform.resource.repository.DeploymentRepository;
import eu.appbahn.platform.resource.repository.ResourceCacheRepository;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceSpec;
import eu.appbahn.shared.crd.ResourceStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceSyncService {

    private static final Logger log = LoggerFactory.getLogger(ResourceSyncService.class);

    private final ResourceCacheRepository resourceCacheRepository;
    private final DeploymentRepository deploymentRepository;
    private final EnvironmentRepository environmentRepository;
    private final ObjectMapper objectMapper;

    public ResourceSyncService(
            ResourceCacheRepository resourceCacheRepository,
            DeploymentRepository deploymentRepository,
            EnvironmentRepository environmentRepository,
            ObjectMapper objectMapper) {
        this.resourceCacheRepository = resourceCacheRepository;
        this.deploymentRepository = deploymentRepository;
        this.environmentRepository = environmentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void syncResource(ResourceSyncRequest request) {
        syncResource(request, null);
    }

    /**
     * Sync a single resource. If a pre-resolved environment is provided it is used directly,
     * avoiding redundant DB lookups when called from {@link #fullSync}.
     */
    @Transactional
    public void syncResource(ResourceSyncRequest request, EnvironmentEntity preResolvedEnv) {
        var env = preResolvedEnv != null
                ? preResolvedEnv
                : environmentRepository
                        .findBySlug(request.getEnvironmentSlug())
                        .orElseThrow(
                                () -> new NotFoundException("Environment not found: " + request.getEnvironmentSlug()));

        ResourceConfig config = objectMapper.convertValue(request.getConfig(), ResourceConfig.class);
        ResourceStatus statusDetail = request.getStatusDetail() != null
                ? objectMapper.convertValue(request.getStatusDetail(), ResourceStatus.class)
                : null;

        ResourcePhase phase = ResourcePhase.valueOf(request.getStatus().name());

        List<ResourceSpec.ResourceLink> links = request.getLinks() != null
                ? request.getLinks().stream()
                        .map(lc -> {
                            var rl = new ResourceSpec.ResourceLink();
                            rl.setResource(lc.getResource());
                            rl.setSecret(lc.getSecret());
                            rl.setEnv(lc.getEnv());
                            return rl;
                        })
                        .toList()
                : List.of();

        var now = Instant.now();
        var existing = resourceCacheRepository.findBySlug(request.getSlug()).orElse(null);
        if (existing != null) {
            existing.setName(request.getName());
            existing.setType(request.getType());
            existing.setConfig(config);
            existing.setLinks(links);
            existing.setStatus(phase);
            existing.setStatusDetail(statusDetail);
            existing.setLastSyncedAt(now);
            existing.setUpdatedAt(now);
            resourceCacheRepository.save(existing);
        } else {
            var entity = new ResourceCacheEntity();
            entity.setSlug(request.getSlug());
            entity.setEnvironmentId(env.getId());
            entity.setName(request.getName());
            entity.setType(request.getType());
            entity.setConfig(config);
            entity.setLinks(links);
            entity.setStatus(phase);
            entity.setStatusDetail(statusDetail);
            entity.setLastSyncedAt(now);
            entity.setCreatedAt(
                    request.getCreatedAt() != null ? request.getCreatedAt().toInstant() : now);
            entity.setUpdatedAt(now);
            resourceCacheRepository.save(entity);
        }
        // Update deployment status using explicit fields from the operator (not resource phase).
        // The operator reports latestDeploymentId + latestDeploymentStatus based on actual
        // K8s Deployment rollout status, avoiding the resource-phase != deployment-status mismatch.
        if (statusDetail != null
                && statusDetail.getLatestDeploymentId() != null
                && statusDetail.getLatestDeploymentStatus() != null) {
            var deploymentStatus = statusDetail.getLatestDeploymentStatus();
            try {
                UUID deploymentId = UUID.fromString(statusDetail.getLatestDeploymentId());
                deploymentRepository
                        .findByIdAndResourceSlug(deploymentId, request.getSlug())
                        .filter(d -> !d.isPrimary())
                        .filter(d -> deploymentStatus != d.getStatus())
                        .ifPresent(d -> {
                            d.setStatus(deploymentStatus);
                            deploymentRepository.save(d);
                            log.info(
                                    "Deployment {} status -> {} for resource {}",
                                    d.getId(),
                                    deploymentStatus,
                                    request.getSlug());

                            // Transfer primary flag when deployment reaches SUCCEEDED
                            if (deploymentStatus == eu.appbahn.shared.crd.DeploymentStatus.SUCCEEDED) {
                                deploymentRepository.transferPrimary(request.getSlug(), d.getId());
                                log.info("Deployment {} became primary for resource {}", d.getId(), request.getSlug());
                            }
                        });
            } catch (IllegalArgumentException e) {
                log.warn(
                        "Invalid latestDeploymentId '{}' for resource {}",
                        statusDetail.getLatestDeploymentId(),
                        request.getSlug());
            }
        }
        log.info("Synced resource: {}", request.getSlug());
    }

    @Transactional
    public void deleteResourceSync(String slug) {
        resourceCacheRepository.deleteById(slug);
        log.info("Deleted resource from cache: {}", slug);
    }

    @Transactional
    public void fullSync(FullResourceSyncRequest request) {
        // Record the sync start time BEFORE processing. During the prune phase, only resources
        // with lastSyncedAt strictly before this timestamp are eligible for deletion. This prevents
        // a race where a concurrent syncResource creates a new cache entry between the upsert loop
        // and the prune query — the concurrent entry will have lastSyncedAt >= syncStartedAt and
        // will be preserved.
        Instant syncStartedAt = Instant.now();

        // Pre-resolve all distinct environment slugs to avoid N+1 queries
        Map<String, EnvironmentEntity> envCache = new HashMap<>();
        for (var res : request.getResources()) {
            envCache.computeIfAbsent(res.getEnvironmentSlug(), slug -> environmentRepository
                    .findBySlug(slug)
                    .orElseThrow(() -> new NotFoundException("Environment not found: " + slug)));
        }

        Set<String> incomingSlugs = new HashSet<>();
        for (var res : request.getResources()) {
            incomingSlugs.add(res.getSlug());
            syncResource(res, envCache.get(res.getEnvironmentSlug()));
        }

        // Delete stale resources in a single query — those belonging to this cluster's
        // environments that were not in the incoming set and have not been synced since we started.
        var clusterEnvIds = environmentRepository.findByTargetCluster(request.getClusterName()).stream()
                .map(env -> env.getId())
                .toList();

        if (!clusterEnvIds.isEmpty()) {
            int deleted = incomingSlugs.isEmpty()
                    ? resourceCacheRepository.deleteAllStaleByEnvironmentIds(clusterEnvIds, syncStartedAt)
                    : resourceCacheRepository.deleteStaleByEnvironmentIds(clusterEnvIds, incomingSlugs, syncStartedAt);
            if (deleted > 0) {
                log.info("Removed {} stale resource(s) from cache during full sync", deleted);
            }
        }
        log.info("Full sync completed for cluster: {}", request.getClusterName());
    }
}

package eu.appbahn.platform.resource.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.internal.model.FullResourceSyncRequest;
import eu.appbahn.platform.api.internal.model.ResourceSyncRequest;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.resource.entity.DeploymentEntity;
import eu.appbahn.platform.resource.entity.ResourceCacheEntity;
import eu.appbahn.platform.resource.repository.DeploymentRepository;
import eu.appbahn.platform.resource.repository.ResourceCacheRepository;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceSpec;
import eu.appbahn.shared.crd.ResourceStatus;
import jakarta.persistence.EntityManager;
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
    private final EntityManager entityManager;

    public ResourceSyncService(
            ResourceCacheRepository resourceCacheRepository,
            DeploymentRepository deploymentRepository,
            EnvironmentRepository environmentRepository,
            ObjectMapper objectMapper,
            EntityManager entityManager) {
        this.resourceCacheRepository = resourceCacheRepository;
        this.deploymentRepository = deploymentRepository;
        this.environmentRepository = environmentRepository;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public void syncResource(ResourceSyncRequest request) {
        syncResource(request, null);
    }

    /** {@code preResolvedEnv} skips the lookup when the caller (fullSync) already has it. */
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
        boolean isFirstSight = existing == null;
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

        // First-sight: materialise the initial DeploymentEntity the API used to create.
        // ResourceService.create pre-generated the deploymentRevision UUID and set it on the
        // CRD spec — the operator carries it through to status.latestDeploymentId.
        if (isFirstSight
                && statusDetail != null
                && statusDetail.getLatestDeploymentId() != null
                && config.getSource() instanceof eu.appbahn.shared.crd.DockerSource docker
                && docker.getImage() != null) {
            try {
                UUID depId = UUID.fromString(statusDetail.getLatestDeploymentId());
                if (deploymentRepository.findById(depId).isEmpty()) {
                    // Flush the cache row before the FK-dependent deployment insert.
                    entityManager.flush();
                    String tag = docker.getTag() != null ? docker.getTag() : "latest";
                    String imageRef = docker.getImage() + ":" + tag;
                    var dep = new DeploymentEntity();
                    dep.setId(depId);
                    dep.setResourceSlug(request.getSlug());
                    dep.setEnvironmentId(env.getId());
                    dep.setSourceRef(imageRef);
                    dep.setImageRef(imageRef);
                    dep.setTriggeredBy(TriggerType.MANUAL);
                    dep.setStatus(eu.appbahn.shared.crd.DeploymentStatus.DEPLOYING);
                    dep.setPrimary(false);
                    deploymentRepository.save(dep);
                    log.info("Materialised initial deployment {} for resource {}", depId, request.getSlug());
                }
            } catch (IllegalArgumentException e) {
                log.warn(
                        "Invalid latestDeploymentId '{}' on first sync of resource {} — skipping initial deployment",
                        statusDetail.getLatestDeploymentId(),
                        request.getSlug());
            }
        }
        // Use the operator's explicit latestDeploymentId/Status (driven by K8s rollout) rather
        // than the resource phase, which would misreport during rolling updates.
        if (statusDetail != null
                && statusDetail.getLatestDeploymentId() != null
                && statusDetail.getLatestDeploymentStatus() != null) {
            var deploymentStatus = statusDetail.getLatestDeploymentStatus();
            try {
                UUID deploymentId = UUID.fromString(statusDetail.getLatestDeploymentId());
                // Intentionally does NOT filter `!isPrimary()`: a primary can transition back
                // to FAILED if the K8s rollout ultimately fails (DeploymentRollbackTest).
                deploymentRepository
                        .findByIdAndResourceSlug(deploymentId, request.getSlug())
                        .filter(d -> deploymentStatus != d.getStatus())
                        .ifPresent(d -> {
                            d.setStatus(deploymentStatus);
                            deploymentRepository.save(d);
                            log.info(
                                    "Deployment {} status -> {} for resource {}",
                                    d.getId(),
                                    deploymentStatus,
                                    request.getSlug());

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
        // Idempotent — ResourceService.delete may have already removed the row.
        resourceCacheRepository.deleteBySlugIfExists(slug);
        log.info("Deleted resource from cache: {}", slug);
    }

    @Transactional
    public void fullSync(FullResourceSyncRequest request) {
        // Capture BEFORE processing: the prune phase only deletes rows whose lastSyncedAt
        // predates this timestamp, so a concurrent syncResource is safe.
        Instant syncStartedAt = Instant.now();

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

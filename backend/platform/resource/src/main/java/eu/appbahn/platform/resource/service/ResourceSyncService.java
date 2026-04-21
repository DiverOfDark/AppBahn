package eu.appbahn.platform.resource.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.resource.entity.DeploymentEntity;
import eu.appbahn.platform.resource.entity.ResourceCacheEntity;
import eu.appbahn.platform.resource.repository.DeploymentRepository;
import eu.appbahn.platform.resource.repository.ResourceCacheRepository;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourceSpec;
import eu.appbahn.shared.crd.ResourceStatus;
import eu.appbahn.tunnel.wire.FullSyncPayload;
import eu.appbahn.tunnel.wire.ResourceSyncPayload;
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
    public void syncResource(ResourceSyncPayload request) {
        syncResource(request, null);
    }

    /** {@code preResolvedEnv} skips the lookup when the caller (fullSync) already has it. */
    @Transactional
    public void syncResource(ResourceSyncPayload request, EnvironmentEntity preResolvedEnv) {
        EnvironmentEntity env = preResolvedEnv != null
                ? preResolvedEnv
                : environmentRepository.findBySlug(request.environmentSlug()).orElse(null);
        if (env == null) {
            // Same race as fullSync: a CR can outlive its env (namespace stays Terminating
            // until finalizers clear). Skip the upsert; the operator's full-sync set-diff
            // will eventually prune the cache row when the CR is gone in K8s too.
            log.info("Skipping sync for {} — environment {} not found", request.slug(), request.environmentSlug());
            return;
        }

        ResourceConfig config =
                request.config() != null ? objectMapper.convertValue(request.config(), ResourceConfig.class) : null;
        ResourceStatus statusDetail = request.statusDetail() != null
                ? objectMapper.convertValue(request.statusDetail(), ResourceStatus.class)
                : null;

        List<ResourceSpec.ResourceLink> links = request.links() != null ? request.links() : List.of();

        var now = Instant.now();
        var existing = resourceCacheRepository.findBySlug(request.slug()).orElse(null);
        boolean isFirstSight = existing == null;
        if (existing != null) {
            existing.setName(request.name());
            existing.setType(request.type());
            existing.setConfig(config);
            existing.setLinks(links);
            existing.setStatus(request.status());
            existing.setStatusDetail(statusDetail);
            existing.setLastSyncedAt(now);
            existing.setUpdatedAt(now);
            resourceCacheRepository.save(existing);
        } else {
            var entity = new ResourceCacheEntity();
            entity.setSlug(request.slug());
            entity.setEnvironmentId(env.getId());
            entity.setName(request.name());
            entity.setType(request.type());
            entity.setConfig(config);
            entity.setLinks(links);
            entity.setStatus(request.status());
            entity.setStatusDetail(statusDetail);
            entity.setLastSyncedAt(now);
            entity.setCreatedAt(request.createdAt() != null ? request.createdAt() : now);
            entity.setUpdatedAt(now);
            resourceCacheRepository.save(entity);
        }

        // First-sight path: a CR we've never seen on the platform side (e.g. direct
        // `kubectl apply` admitted by the operator's webhook). Materialise the initial
        // DeploymentEntity from statusDetail.latestDeploymentId.
        if (isFirstSight
                && statusDetail != null
                && statusDetail.getLatestDeploymentId() != null
                && config != null
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
                    dep.setResourceSlug(request.slug());
                    dep.setEnvironmentId(env.getId());
                    dep.setSourceRef(imageRef);
                    dep.setImageRef(imageRef);
                    dep.setTriggeredBy(TriggerType.MANUAL);
                    dep.setStatus(eu.appbahn.shared.crd.DeploymentStatus.DEPLOYING);
                    dep.setPrimary(false);
                    deploymentRepository.save(dep);
                    log.info("Materialised initial deployment {} for resource {}", depId, request.slug());
                }
            } catch (IllegalArgumentException e) {
                log.warn(
                        "Invalid latestDeploymentId '{}' on first sync of resource {} — skipping initial deployment",
                        statusDetail.getLatestDeploymentId(),
                        request.slug());
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
                        .findByIdAndResourceSlug(deploymentId, request.slug())
                        .filter(d -> deploymentStatus != d.getStatus())
                        .ifPresent(d -> {
                            d.setStatus(deploymentStatus);
                            deploymentRepository.save(d);
                            log.info(
                                    "Deployment {} status -> {} for resource {}",
                                    d.getId(),
                                    deploymentStatus,
                                    request.slug());

                            if (deploymentStatus == eu.appbahn.shared.crd.DeploymentStatus.SUCCEEDED) {
                                deploymentRepository.transferPrimary(request.slug(), d.getId());
                                log.info("Deployment {} became primary for resource {}", d.getId(), request.slug());
                            }
                        });
            } catch (IllegalArgumentException e) {
                log.warn(
                        "Invalid latestDeploymentId '{}' for resource {}",
                        statusDetail.getLatestDeploymentId(),
                        request.slug());
            }
        }
        log.info("Synced resource: {}", request.slug());
    }

    @Transactional
    public void deleteResourceSync(String slug) {
        // Idempotent — ResourceService.delete may have already removed the row.
        resourceCacheRepository.deleteBySlugIfExists(slug);
        log.info("Deleted resource from cache: {}", slug);
    }

    @Transactional
    public void fullSync(FullSyncPayload request) {
        // Capture BEFORE processing: the prune phase only deletes rows whose lastSyncedAt
        // predates this timestamp, so a concurrent syncResource is safe.
        Instant syncStartedAt = Instant.now();

        List<ResourceSyncPayload> resources = request.resources() != null ? request.resources() : List.of();

        // CRs may linger in K8s after their environment is deleted (namespace stays in
        // Terminating until finalizers clear). Skip those so the set-diff still runs;
        // throwing aborts the whole tx and the prune below never happens, which leaves
        // unrelated stale rows in the cache (e.g. a freshly-deleted resource never
        // disappears until the next successful full sync).
        Map<String, EnvironmentEntity> envCache = new HashMap<>();
        Set<String> missingEnvs = new HashSet<>();
        for (var res : resources) {
            envCache.computeIfAbsent(
                    res.environmentSlug(),
                    slug -> environmentRepository.findBySlug(slug).orElse(null));
            if (envCache.get(res.environmentSlug()) == null) {
                missingEnvs.add(res.environmentSlug());
            }
        }
        if (!missingEnvs.isEmpty()) {
            log.info("Full sync: skipping resources from {} unknown env(s): {}", missingEnvs.size(), missingEnvs);
        }

        Set<String> incomingSlugs = new HashSet<>();
        for (var res : resources) {
            EnvironmentEntity env = envCache.get(res.environmentSlug());
            if (env == null) {
                continue;
            }
            incomingSlugs.add(res.slug());
            syncResource(res, env);
        }

        var clusterEnvIds = environmentRepository.findByTargetCluster(request.clusterName()).stream()
                .map(EnvironmentEntity::getId)
                .toList();

        if (!clusterEnvIds.isEmpty()) {
            int deleted = incomingSlugs.isEmpty()
                    ? resourceCacheRepository.deleteAllStaleByEnvironmentIds(clusterEnvIds, syncStartedAt)
                    : resourceCacheRepository.deleteStaleByEnvironmentIds(clusterEnvIds, incomingSlugs, syncStartedAt);
            if (deleted > 0) {
                log.info("Removed {} stale resource(s) from cache during full sync", deleted);
            }
        }
        log.info("Full sync completed for cluster: {}", request.clusterName());
    }
}

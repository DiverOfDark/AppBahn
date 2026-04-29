package eu.appbahn.platform.resource.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.resource.entity.ImageSourceCacheEntity;
import eu.appbahn.platform.resource.repository.ImageSourceCacheRepository;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.shared.tunnel.ImageSourceSyncPayload;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists ImageSource CR snapshots into {@code image_source_cache}. Mirrors
 * {@link ResourceSyncService} for the Resource CRD. The CR is authoritative — this service
 * is observation-only on the platform side.
 */
@Service
public class ImageSourceSyncService {

    private static final Logger log = LoggerFactory.getLogger(ImageSourceSyncService.class);

    private final ImageSourceCacheRepository imageSourceCacheRepository;
    private final EnvironmentRepository environmentRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public ImageSourceSyncService(
            ImageSourceCacheRepository imageSourceCacheRepository,
            EnvironmentRepository environmentRepository,
            ObjectMapper objectMapper,
            EntityManager entityManager) {
        this.imageSourceCacheRepository = imageSourceCacheRepository;
        this.environmentRepository = environmentRepository;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public void syncImageSourceFromCluster(ImageSourceSyncPayload payload, String expectedClusterName) {
        if (payload == null || payload.slug() == null || payload.slug().isBlank()) {
            log.warn("Skipping ImageSource sync — missing slug");
            return;
        }
        EnvironmentEntity env = null;
        if (payload.environmentSlug() != null && !payload.environmentSlug().isBlank()) {
            env = environmentRepository.findBySlug(payload.environmentSlug()).orElse(null);
            if (env != null && expectedClusterName != null && !expectedClusterName.equals(env.getTargetCluster())) {
                throw new ClusterOwnershipException(String.format(
                        "ImageSource %s (env %s) belongs to cluster %s, not %s",
                        payload.slug(), env.getSlug(), env.getTargetCluster(), expectedClusterName));
            }
        }

        Instant now = Instant.now();
        var existing = imageSourceCacheRepository.findBySlug(payload.slug()).orElse(null);
        var entity = existing != null ? existing : new ImageSourceCacheEntity();
        entity.setSlug(payload.slug());
        entity.setEnvironmentId(env != null ? env.getId() : null);
        // Derived from envSlug because the wire payload doesn't carry namespace explicitly;
        // an ImageSource CR sitting outside any environment namespace produces null here.
        entity.setNamespace(
                payload.environmentSlug() != null && !payload.environmentSlug().isBlank()
                        ? "abp-" + payload.environmentSlug()
                        : null);
        entity.setSpec(payload.spec());
        entity.setStatus(payload.status());
        entity.setObservedCommit(payload.status() != null ? payload.status().getObservedCommit() : null);
        entity.setImageRef(
                payload.status() != null && payload.status().getLatestArtifact() != null
                        ? payload.status().getLatestArtifact().getImageRef()
                        : null);
        entity.setLastPolledAt(payload.status() != null ? payload.status().getLastPollAt() : null);
        entity.setLastSyncedAt(now);
        if (existing == null) {
            entity.setCreatedAt(payload.createdAt() != null ? payload.createdAt() : now);
        }
        entity.setUpdatedAt(now);
        if (existing != null) {
            entityManager.detach(existing);
        }
        imageSourceCacheRepository.upsertFromSync(entity, objectMapper);
        log.info("Synced ImageSource: {}", payload.slug());
    }

    @Transactional
    public void deleteImageSourceFromCluster(String slug, String expectedClusterName) {
        if (slug == null || slug.isBlank()) {
            return;
        }
        var existing = imageSourceCacheRepository.findBySlug(slug).orElse(null);
        if (existing == null) {
            return;
        }
        if (existing.getEnvironmentId() != null && expectedClusterName != null) {
            var env =
                    environmentRepository.findById(existing.getEnvironmentId()).orElse(null);
            if (env != null && !expectedClusterName.equals(env.getTargetCluster())) {
                throw new ClusterOwnershipException(String.format(
                        "ImageSource %s (env %s) belongs to cluster %s, not %s",
                        slug, env.getSlug(), env.getTargetCluster(), expectedClusterName));
            }
        }
        imageSourceCacheRepository.deleteBySlugIfExists(slug);
        log.info("Deleted ImageSource from cache: {}", slug);
    }
}

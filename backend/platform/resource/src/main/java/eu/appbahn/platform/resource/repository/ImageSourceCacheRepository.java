package eu.appbahn.platform.resource.repository;

import eu.appbahn.platform.resource.entity.ImageSourceCacheEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImageSourceCacheRepository extends JpaRepository<ImageSourceCacheEntity, String> {

    Optional<ImageSourceCacheEntity> findBySlug(String slug);

    List<ImageSourceCacheEntity> findByEnvironmentId(UUID environmentId);

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM image_source_cache WHERE slug = :slug")
    int deleteBySlugIfExists(@Param("slug") String slug);

    /**
     * UPSERT used by the operator sync: insert on first sight, otherwise overwrite every column
     * regardless of the cache's current optimistic-lock {@code version}. The CR is the source of
     * truth, so an "always wins" write is correct — but {@code version} is bumped so platform
     * reads observe the change and any in-flight platform write trips its {@code @Version}
     * check on flush. Mirrors {@code resource_cache.upsertFromSync}.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
                    INSERT INTO image_source_cache (
                        slug, environment_id, namespace, spec, status, observed_commit, image_ref,
                        last_polled_at, last_synced_at, created_at, updated_at, version
                    ) VALUES (
                        :slug, :environmentId, :namespace, CAST(:spec AS jsonb), CAST(:status AS jsonb),
                        :observedCommit, :imageRef, :lastPolledAt, :lastSyncedAt, :createdAt, :updatedAt, 0
                    )
                    ON CONFLICT (slug) DO UPDATE SET
                        environment_id  = EXCLUDED.environment_id,
                        namespace       = EXCLUDED.namespace,
                        spec            = EXCLUDED.spec,
                        status          = EXCLUDED.status,
                        observed_commit = EXCLUDED.observed_commit,
                        image_ref       = EXCLUDED.image_ref,
                        last_polled_at  = EXCLUDED.last_polled_at,
                        last_synced_at  = EXCLUDED.last_synced_at,
                        updated_at      = EXCLUDED.updated_at,
                        version         = image_source_cache.version + 1
                    """)
    int upsertFromSync(
            @Param("slug") String slug,
            @Param("environmentId") UUID environmentId,
            @Param("namespace") String namespace,
            @Param("spec") String specJson,
            @Param("status") String statusJson,
            @Param("observedCommit") String observedCommit,
            @Param("imageRef") String imageRef,
            @Param("lastPolledAt") Instant lastPolledAt,
            @Param("lastSyncedAt") Instant lastSyncedAt,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt);

    default int upsertFromSync(
            ImageSourceCacheEntity entity, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        try {
            String specJson = entity.getSpec() != null ? objectMapper.writeValueAsString(entity.getSpec()) : "{}";
            String statusJson = entity.getStatus() != null ? objectMapper.writeValueAsString(entity.getStatus()) : null;
            return upsertFromSync(
                    entity.getSlug(),
                    entity.getEnvironmentId(),
                    entity.getNamespace(),
                    specJson,
                    statusJson,
                    entity.getObservedCommit(),
                    entity.getImageRef(),
                    entity.getLastPolledAt(),
                    entity.getLastSyncedAt(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialise image_source_cache JSONB columns for " + entity.getSlug(), e);
        }
    }
}

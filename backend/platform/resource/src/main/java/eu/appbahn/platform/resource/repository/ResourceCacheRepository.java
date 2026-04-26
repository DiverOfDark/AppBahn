package eu.appbahn.platform.resource.repository;

import eu.appbahn.platform.resource.entity.ResourceCacheEntity;
import eu.appbahn.shared.crd.ResourcePhase;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceCacheRepository extends JpaRepository<ResourceCacheEntity, String> {

    Optional<ResourceCacheEntity> findBySlug(String slug);

    Page<ResourceCacheEntity> findByEnvironmentId(UUID environmentId, Pageable pageable);

    List<ResourceCacheEntity> findByEnvironmentId(UUID environmentId);

    long countByEnvironmentId(UUID environmentId);

    void deleteBySlug(String slug);

    /**
     * Native DELETE that tolerates the row being absent (race with concurrent deletes from the
     * operator's sync callback). Unlike Hibernate's managed delete, this doesn't assert on the
     * affected row count.
     */
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM resource_cache WHERE slug = :slug")
    int deleteBySlugIfExists(@Param("slug") String slug);

    List<ResourceCacheEntity> findByEnvironmentIdIn(Collection<UUID> environmentIds);

    @Query(
            nativeQuery = true,
            value =
                    "SELECT * FROM resource_cache WHERE EXISTS (SELECT 1 FROM jsonb_array_elements(links) elem WHERE elem ->> 'resource' = :resourceSlug)")
    List<ResourceCacheEntity> findByLinkedResourceSlug(@Param("resourceSlug") String resourceSlug);

    /**
     * Update only the status column via native SQL. Unlike Hibernate's entity save, this does not
     * check the optimistic lock version -- it always succeeds regardless of concurrent operator
     * sync writes. The next sync cycle will overwrite the status with the authoritative value
     * from the CRD anyway, so a brief divergence is harmless.
     */
    @Modifying
    @Query(nativeQuery = true, value = "UPDATE resource_cache SET status = :status WHERE slug = :slug")
    int updateStatusBySlug(@Param("slug") String slug, @Param("status") String status);

    @Modifying
    @Query(
            nativeQuery = true,
            value =
                    "DELETE FROM resource_cache WHERE environment_id IN (:envIds) AND slug NOT IN (:incomingSlugs) AND (last_synced_at IS NULL OR last_synced_at < :syncStartedAt)")
    int deleteStaleByEnvironmentIds(
            @Param("envIds") Collection<UUID> envIds,
            @Param("incomingSlugs") Collection<String> incomingSlugs,
            @Param("syncStartedAt") java.time.Instant syncStartedAt);

    @Modifying
    @Query(
            nativeQuery = true,
            value =
                    "DELETE FROM resource_cache WHERE environment_id IN (:envIds) AND (last_synced_at IS NULL OR last_synced_at < :syncStartedAt)")
    int deleteAllStaleByEnvironmentIds(
            @Param("envIds") Collection<UUID> envIds, @Param("syncStartedAt") java.time.Instant syncStartedAt);

    /**
     * UPSERT used by the operator sync: insert on first sight, otherwise overwrite every column
     * regardless of the cache's current optimistic-lock {@code version}. The CRD is the source of
     * truth, so an "always wins" write is correct — but we still bump {@code version} so platform
     * reads observe the change and any in-flight platform write trips its {@code @Version} check
     * on flush. JSONB columns take their string parameters through {@code ?::jsonb} casts.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
                    INSERT INTO resource_cache (
                        slug, environment_id, name, type, config, links, status, status_detail,
                        last_synced_at, created_at, updated_at, version
                    ) VALUES (
                        :slug, :environmentId, :name, :type, CAST(:config AS jsonb), CAST(:links AS jsonb),
                        :status, CAST(:statusDetail AS jsonb), :lastSyncedAt, :createdAt, :updatedAt, 0
                    )
                    ON CONFLICT (slug) DO UPDATE SET
                        environment_id = EXCLUDED.environment_id,
                        name           = EXCLUDED.name,
                        type           = EXCLUDED.type,
                        config         = EXCLUDED.config,
                        links          = EXCLUDED.links,
                        status         = EXCLUDED.status,
                        status_detail  = EXCLUDED.status_detail,
                        last_synced_at = EXCLUDED.last_synced_at,
                        updated_at     = EXCLUDED.updated_at,
                        version        = resource_cache.version + 1
                    """)
    int upsertFromSync(
            @Param("slug") String slug,
            @Param("environmentId") UUID environmentId,
            @Param("name") String name,
            @Param("type") String type,
            @Param("config") String configJson,
            @Param("links") String linksJson,
            @Param("status") String status,
            @Param("statusDetail") String statusDetailJson,
            @Param("lastSyncedAt") Instant lastSyncedAt,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt);

    default int upsertFromSync(ResourceCacheEntity entity, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        try {
            String configJson = entity.getConfig() != null ? objectMapper.writeValueAsString(entity.getConfig()) : "{}";
            String linksJson =
                    objectMapper.writeValueAsString(entity.getLinks() != null ? entity.getLinks() : List.of());
            String statusDetailJson =
                    entity.getStatusDetail() != null ? objectMapper.writeValueAsString(entity.getStatusDetail()) : null;
            ResourcePhase phase = entity.getStatus();
            return upsertFromSync(
                    entity.getSlug(),
                    entity.getEnvironmentId(),
                    entity.getName(),
                    entity.getType(),
                    configJson,
                    linksJson,
                    phase != null ? phase.name() : null,
                    statusDetailJson,
                    entity.getLastSyncedAt(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialise resource_cache JSONB columns for " + entity.getSlug(), e);
        }
    }
}

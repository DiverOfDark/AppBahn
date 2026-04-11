package eu.appbahn.platform.resource.repository;

import eu.appbahn.platform.resource.entity.ResourceCacheEntity;
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

    List<ResourceCacheEntity> findByEnvironmentIdIn(Collection<UUID> environmentIds);

    @Query(
            nativeQuery = true,
            value =
                    "SELECT * FROM resource_cache WHERE EXISTS (SELECT 1 FROM jsonb_array_elements(links) elem WHERE elem ->> 'resource' = :resourceSlug)")
    List<ResourceCacheEntity> findByLinkedResourceSlug(@Param("resourceSlug") String resourceSlug);

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
}

package eu.appbahn.platform.resource.repository;

import eu.appbahn.platform.resource.entity.DeploymentEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeploymentRepository extends JpaRepository<DeploymentEntity, UUID> {

    Page<DeploymentEntity> findByResourceSlug(String resourceSlug, Pageable pageable);

    Optional<DeploymentEntity> findByIdAndResourceSlug(UUID id, String resourceSlug);

    Optional<DeploymentEntity> findByResourceSlugAndPrimaryTrue(String resourceSlug);

    Optional<DeploymentEntity> findTopByResourceSlugOrderByCreatedAtDesc(String resourceSlug);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE DeploymentEntity d SET d.primary = (d.id = :newPrimaryId) "
            + "WHERE d.resourceSlug = :slug AND (d.primary = true OR d.id = :newPrimaryId)")
    void transferPrimary(@Param("slug") String resourceSlug, @Param("newPrimaryId") UUID newPrimaryId);

    long countByResourceSlug(String resourceSlug);

    /**
     * Delete deployment audit rows that are eligible for retention pruning. A row is eligible iff
     * <ul>
     *   <li>its {@code lifecycle} is one of {@code SUPERSEDED}, {@code FAILED}, {@code CANCELED}
     *       (in-flight / current-state rows are never pruned), and
     *   <li>it is older than the most recent {@code maxBuildsPerResource} rows for the same
     *       {@code resource_slug} (ranked by {@code created_at DESC}), and
     *   <li>its id is not referenced by any Resource's {@code spec.pinnedRelease.pinnedFromDeploymentId}
     *       — pinned rows are the audit source of current state and must survive even if old.
     * </ul>
     *
     * <p>Single SQL pass via window function — no per-Resource loop in Java.
     *
     * @return number of rows deleted
     */
    @Modifying
    @Query(nativeQuery = true, value = """
                    WITH ranked AS (
                        SELECT id,
                               ROW_NUMBER() OVER (
                                   PARTITION BY resource_slug
                                   ORDER BY created_at DESC
                               ) AS rn
                        FROM deployment
                        WHERE lifecycle IN ('SUPERSEDED', 'FAILED', 'CANCELED')
                          AND id NOT IN (
                              SELECT (pinned_release ->> 'pinnedFromDeploymentId')::uuid
                              FROM resource_cache
                              WHERE pinned_release IS NOT NULL
                                AND pinned_release ->> 'pinnedFromDeploymentId' IS NOT NULL
                          )
                    )
                    DELETE FROM deployment
                    WHERE id IN (SELECT id FROM ranked WHERE rn > :max)
                    """)
    int pruneByRetentionPolicy(@Param("max") int maxBuildsPerResource);
}

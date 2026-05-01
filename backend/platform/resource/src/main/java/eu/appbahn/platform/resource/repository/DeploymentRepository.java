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

    Optional<DeploymentEntity> findFirstByResourceSlugAndImageRefOrderByCreatedAtDesc(
            String resourceSlug, String imageRef);

    /**
     * Defense-in-depth lookup keyed by the (image source, commit) triple instead of the
     * platform-side row id. The operator carries a {@code deploymentId} on every
     * {@link eu.appbahn.platform.api.tunnel.BuildLifecycleEvent} as the canonical correlator —
     * but during a reconcile race two events can land with different ids for the same build.
     * When that happens the {@link eu.appbahn.platform.resource.service.BuildLifecycleHandler}
     * falls back to this query so the late-arriving event lands on the existing audit row
     * instead of creating a duplicate.
     *
     * <p>{@code lifecycle IS NULL} matches the very first event of a build (lifecycle hasn't been
     * stamped yet on the row); the in-flight set
     * ({@code QUEUED}, {@code BUILDING}, {@code BUILT}, {@code ACTIVATING}) covers the build half;
     * terminal rows ({@code FAILED}, {@code SUPERSEDED}, {@code CANCELED}, {@code ACTIVE}) are
     * intentionally excluded — a fresh event for a (source, commit) whose previous build
     * terminated should mint a new audit row, not resurrect an old one.
     */
    @Query("""
            SELECT d FROM DeploymentEntity d
            WHERE d.imageSourceNamespace = :namespace
              AND d.imageSourceName = :name
              AND d.sourceRef = :sourceCommit
              AND (d.lifecycle IS NULL OR d.lifecycle IN (
                  eu.appbahn.shared.crd.imagesource.BuildLifecycle.QUEUED,
                  eu.appbahn.shared.crd.imagesource.BuildLifecycle.BUILDING,
                  eu.appbahn.shared.crd.imagesource.BuildLifecycle.BUILT,
                  eu.appbahn.shared.crd.imagesource.BuildLifecycle.ACTIVATING))
            ORDER BY d.createdAt ASC
            """)
    java.util.List<DeploymentEntity> findInFlightByImageSourceAndCommit(
            @Param("namespace") String namespace,
            @Param("name") String name,
            @Param("sourceCommit") String sourceCommit);

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

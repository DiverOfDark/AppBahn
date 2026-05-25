package eu.appbahn.platform.resource.repository;

import eu.appbahn.platform.resource.entity.DeploymentEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeploymentRepository extends JpaRepository<DeploymentEntity, UUID> {

    Page<DeploymentEntity> findByResourceSlug(String resourceSlug, Pageable pageable);

    /**
     * Server-side lifecycle-tab filter for the Deploys tab. Either {@code lifecycles} matches
     * (Succeeded / Failed tabs) or {@code triggerRollback=true} narrows to {@code triggered_by =
     * ROLLBACK} (Rollback tab) — exactly one branch fires per call. {@code lifecycles} must be
     * non-empty even on the rollback branch (any non-empty placeholder works) because JPQL's
     * {@code IN :param} chokes on an empty collection.
     */
    @Query("""
            SELECT d FROM DeploymentEntity d
            WHERE d.resourceSlug = :slug
              AND ((:triggerRollback = TRUE AND d.triggeredBy = eu.appbahn.platform.api.TriggerType.ROLLBACK)
                   OR (:triggerRollback = FALSE AND d.lifecycle IN :lifecycles))
            """)
    Page<DeploymentEntity> findByResourceSlugFiltered(
            @Param("slug") String resourceSlug,
            @Param("lifecycles") Collection<eu.appbahn.shared.crd.imagesource.BuildLifecycle> lifecycles,
            @Param("triggerRollback") boolean triggerRollback,
            Pageable pageable);

    List<DeploymentEntity> findByEnvironmentId(UUID environmentId, Pageable pageable);

    /**
     * Server-side fold of {@code MAX(deployment.created_at)} grouped by {@code resource_slug},
     * scoped to a single environment. Powers {@code Resource.lastDeploymentAt} on the resource
     * listing payload without per-row N+1 queries. Resources with no deployments are absent
     * from the returned map — the caller treats absent as {@code null}.
     */
    @Query("SELECT d.resourceSlug AS slug, MAX(d.createdAt) AS latest "
            + "FROM DeploymentEntity d "
            + "WHERE d.resourceSlug IN :slugs "
            + "GROUP BY d.resourceSlug")
    List<SlugLatestProjection> findLatestDeploymentAtBySlugs(@Param("slugs") Collection<String> slugs);

    default Map<String, Instant> findLatestDeploymentAtBySlugsAsMap(Collection<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return Map.of();
        }
        return findLatestDeploymentAtBySlugs(slugs).stream()
                .collect(Collectors.toMap(SlugLatestProjection::getSlug, SlugLatestProjection::getLatest));
    }

    interface SlugLatestProjection {
        String getSlug();

        Instant getLatest();
    }

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

    /**
     * When a new release becomes ACTIVE, any older in-flight rows for the same resource_slug
     * are by definition obsolete — their rollout was overtaken before it could reach ACTIVE.
     * Flip their lifecycle to {@link eu.appbahn.shared.crd.imagesource.BuildLifecycle#SUPERSEDED}
     * in one SQL pass so the Deploys tab doesn't keep showing them stuck on {@code Activating}.
     * Only in-flight rows are touched ({@code BUILT}, {@code ACTIVATING}); terminal rows
     * (previous {@code ACTIVE}, {@code FAILED}, …) keep their historical lifecycle.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE DeploymentEntity d "
            + "SET d.lifecycle = eu.appbahn.shared.crd.imagesource.BuildLifecycle.SUPERSEDED, "
            + "    d.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE d.resourceSlug = :slug "
            + "  AND d.id <> :exceptId "
            + "  AND d.lifecycle IN ("
            + "      eu.appbahn.shared.crd.imagesource.BuildLifecycle.BUILT,"
            + "      eu.appbahn.shared.crd.imagesource.BuildLifecycle.ACTIVATING)")
    int supersedeInFlight(@Param("slug") String resourceSlug, @Param("exceptId") UUID exceptId);

    long countByResourceSlug(String resourceSlug);

    /**
     * One row per UTC calendar day inside the sliding window {@code [since, +∞)} with non-zero
     * deployment counts. Days with no deployments are omitted; the service-layer pads the
     * result so the histogram has a stable bar count.
     *
     * <p>Aggregation runs entirely in Postgres via
     * {@code date_trunc('day', created_at AT TIME ZONE 'UTC')} — Java never iterates over the
     * {@code deployment} table for stats.
     */
    @Query(nativeQuery = true, value = """
                    SELECT
                        date_trunc('day', created_at AT TIME ZONE 'UTC')::date AS day,
                        COUNT(*) AS deploys,
                        COUNT(*) FILTER (WHERE lifecycle IN ('ACTIVE', 'BUILT')) AS success,
                        COUNT(*) FILTER (WHERE lifecycle IN ('FAILED', 'CANCELED')) AS failure
                    FROM deployment
                    WHERE resource_slug = :slug
                      AND created_at >= :since
                    GROUP BY day
                    ORDER BY day ASC
                    """)
    List<DeploymentStatsBucketRow> aggregateDailyBuckets(
            @Param("slug") String resourceSlug, @Param("since") Instant since);

    /** Window-wide aggregate matching {@link #aggregateDailyBuckets}'s filter. */
    @Query(nativeQuery = true, value = """
                    SELECT
                        COUNT(*) AS deploys,
                        COUNT(*) FILTER (WHERE lifecycle IN ('ACTIVE', 'BUILT')) AS success,
                        COUNT(*) FILTER (WHERE lifecycle IN ('FAILED', 'CANCELED')) AS failure,
                        COUNT(*) FILTER (WHERE triggered_by = 'ROLLBACK') AS rollback
                    FROM deployment
                    WHERE resource_slug = :slug
                      AND created_at >= :since
                    """)
    DeploymentStatsTotalsRow aggregateTotals(@Param("slug") String resourceSlug, @Param("since") Instant since);

    /** Per-day projection for {@link #aggregateDailyBuckets}. */
    interface DeploymentStatsBucketRow {
        java.sql.Date getDay();

        long getDeploys();

        long getSuccess();

        long getFailure();
    }

    /** Window-wide projection for {@link #aggregateTotals}. */
    interface DeploymentStatsTotalsRow {
        long getDeploys();

        long getSuccess();

        long getFailure();

        long getRollback();
    }

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

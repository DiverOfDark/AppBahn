package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.AuditAction;
import eu.appbahn.platform.api.AuditTargetType;
import eu.appbahn.platform.api.Deployment;
import eu.appbahn.platform.api.TriggerType;
import eu.appbahn.platform.api.resource.DeploymentLifecycleFilter;
import eu.appbahn.platform.api.resource.DeploymentStats;
import eu.appbahn.platform.api.resource.DeploymentStatsBucket;
import eu.appbahn.platform.api.resource.DeploymentStatsTotals;
import eu.appbahn.platform.api.resource.PagedDeploymentResponse;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.ConflictException;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.exception.ValidationException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.common.util.PagedResponseUtil;
import eu.appbahn.platform.common.util.PaginationUtil;
import eu.appbahn.platform.resource.entity.DeploymentEntity;
import eu.appbahn.platform.resource.entity.ImageSourceCacheEntity;
import eu.appbahn.platform.resource.repository.DeploymentRepository;
import eu.appbahn.platform.resource.repository.ImageSourceCacheRepository;
import eu.appbahn.platform.workspace.service.EnvironmentLookupService;
import eu.appbahn.platform.workspace.service.PermissionService;
import eu.appbahn.shared.crd.imagesource.BuildLifecycle;
import eu.appbahn.shared.model.MemberRole;
import eu.appbahn.shared.util.UuidV7;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deployment audit + lifecycle operations. Read paths surface paginated rows and a server-side
 * aggregation for the Deploys-tab histogram; write paths cover user-initiated cancel/retry of a
 * deployment, both of which mutate the audit row in-transaction and emit a tunnel command for
 * the operator to take the matching in-cluster action.
 *
 * <p>Build-lifecycle transitions driven by the operator land in {@link BuildLifecycleHandler}, not
 * here — this service is the user-facing surface only.
 */
@Service
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);

    /** Phases where cancel is still allowed. Past this the rollout owns the row. */
    private static final EnumSet<BuildLifecycle> CANCELLABLE_PHASES =
            EnumSet.of(BuildLifecycle.QUEUED, BuildLifecycle.BUILDING);

    private static final int MIN_STATS_WINDOW_DAYS = 1;
    private static final int MAX_STATS_WINDOW_DAYS = 90;
    private static final int DEFAULT_STATS_WINDOW_DAYS = 30;

    /** Default for {@code GET /environments/{slug}/deployments?limit=…} when caller omits the param. */
    private static final int DEFAULT_ENV_DEPLOYMENT_LIMIT = 20;

    private final DeploymentRepository deploymentRepository;
    private final ImageSourceCacheRepository imageSourceCacheRepository;
    private final ResourcePermissionHelper resourcePermissionHelper;
    private final AuditLogService auditLogService;
    private final DeploymentNudger deploymentNudger;
    private final EnvironmentLookupService environmentLookupService;
    private final PermissionService permissionService;

    public DeploymentService(
            DeploymentRepository deploymentRepository,
            ImageSourceCacheRepository imageSourceCacheRepository,
            ResourcePermissionHelper resourcePermissionHelper,
            AuditLogService auditLogService,
            DeploymentNudger deploymentNudger,
            EnvironmentLookupService environmentLookupService,
            PermissionService permissionService) {
        this.deploymentRepository = deploymentRepository;
        this.imageSourceCacheRepository = imageSourceCacheRepository;
        this.resourcePermissionHelper = resourcePermissionHelper;
        this.auditLogService = auditLogService;
        this.deploymentNudger = deploymentNudger;
        this.environmentLookupService = environmentLookupService;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public Deployment get(String resourceSlug, UUID deploymentId, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(resourceSlug, ctx, MemberRole.VIEWER);

        var entity = deploymentRepository
                .findByIdAndResourceSlug(deploymentId, resourceSlug)
                .orElseThrow(() -> new NotFoundException("Deployment not found: " + deploymentId));

        return ResourceEntityMapper.toApi(entity, resolved.env().getSlug());
    }

    @Transactional(readOnly = true)
    public PagedDeploymentResponse list(
            String resourceSlug,
            DeploymentLifecycleFilter lifecycle,
            Integer page,
            Integer size,
            String sort,
            AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(resourceSlug, ctx, MemberRole.VIEWER);
        var env = resolved.env();

        var pageable = PaginationUtil.toPageable(page, size, sort, Sort.by(Sort.Direction.DESC, "createdAt"));
        DeploymentLifecycleFilter effective = lifecycle == null ? DeploymentLifecycleFilter.ALL : lifecycle;
        var result = effective == DeploymentLifecycleFilter.ALL
                ? deploymentRepository.findByResourceSlug(resourceSlug, pageable)
                : deploymentRepository.findByResourceSlugFiltered(
                        resourceSlug,
                        lifecyclesFor(effective),
                        effective == DeploymentLifecycleFilter.ROLLBACK,
                        pageable);

        return PagedResponseUtil.build(
                result,
                e -> ResourceEntityMapper.toApi(e, env.getSlug()),
                new PagedDeploymentResponse(),
                PagedDeploymentResponse::setContent,
                PagedDeploymentResponse::setPage,
                PagedDeploymentResponse::setSize,
                PagedDeploymentResponse::setTotalElements,
                PagedDeploymentResponse::setTotalPages);
    }

    /**
     * Most recent deployments across every resource in {@code environmentSlug}, ordered by
     * {@code createdAt DESC}, capped at {@code limit} (default {@link #DEFAULT_ENV_DEPLOYMENT_LIMIT}).
     * Caller-facing use: the {@code limit=1} pipeline panel and short activity feeds. Returns a
     * single-page response with the page metadata pinned to the slice — the env feed isn't
     * paginated, so {@code totalElements} mirrors {@code content.size}.
     */
    @Transactional(readOnly = true)
    public PagedDeploymentResponse listByEnvironment(String environmentSlug, Integer limit, AuthContext ctx) {
        var env = environmentLookupService.findBySlug(environmentSlug);
        permissionService.requireEnvironmentRole(ctx, env.getId(), MemberRole.VIEWER);

        int capped = limit != null ? limit : DEFAULT_ENV_DEPLOYMENT_LIMIT;
        var pageable = PageRequest.of(0, capped, Sort.by(Sort.Direction.DESC, "createdAt"));
        var rows = deploymentRepository.findByEnvironmentId(env.getId(), pageable);

        var response = new PagedDeploymentResponse();
        response.setContent(rows.stream()
                .map(e -> ResourceEntityMapper.toApi(e, env.getSlug()))
                .toList());
        response.setPage(0);
        response.setSize(capped);
        response.setTotalElements((long) rows.size());
        response.setTotalPages(1);
        return response;
    }

    @Transactional(readOnly = true)
    public DeploymentStats stats(String resourceSlug, Integer windowDays, AuthContext ctx) {
        resourcePermissionHelper.resolve(resourceSlug, ctx, MemberRole.VIEWER);
        int window = clampWindow(windowDays);
        Instant since = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(window - 1L, ChronoUnit.DAYS);

        var rawBuckets = deploymentRepository.aggregateDailyBuckets(resourceSlug, since);
        var totalsRow = deploymentRepository.aggregateTotals(resourceSlug, since);

        var stats = new DeploymentStats();
        stats.setWindowDays(window);
        stats.setBuckets(padBuckets(rawBuckets, since, window));
        stats.setTotals(toTotals(totalsRow));
        return stats;
    }

    @Transactional
    public void cancel(String resourceSlug, UUID deploymentId, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(resourceSlug, ctx, MemberRole.EDITOR);
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        var entity = deploymentRepository
                .findByIdAndResourceSlug(deploymentId, resourceSlug)
                .orElseThrow(() -> new NotFoundException("Deployment not found: " + deploymentId));

        BuildLifecycle current = entity.getLifecycle();
        if (current != null && current.isTerminal()) {
            throw new ConflictException("cannot cancel a deployment that has already finished");
        }
        if (current != null && !CANCELLABLE_PHASES.contains(current)) {
            throw new ConflictException("cannot cancel a deployment that is already rolling out");
        }

        entity.setLifecycle(BuildLifecycle.CANCELED);
        deploymentRepository.save(entity);

        ImageSourceCacheEntity bound = imageSourceCacheRepository
                .findBySlug(resourceSlug)
                .orElseThrow(() -> new NotFoundException("Bound ImageSource not found for resource: " + resourceSlug));
        deploymentNudger.cancelBuild(bound.getNamespace(), bound.getSlug(), deploymentId.toString());

        auditLogService
                .audit(ctx, AuditAction.DEPLOYMENT_CANCELED)
                .target(AuditTargetType.RESOURCE, resourceSlug)
                .inWorkspace(workspaceId)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .change("deploymentId", "", deploymentId.toString())
                .save();

        log.info("Cancelled deployment {} for resource {}", deploymentId, resourceSlug);
    }

    @Transactional
    public Deployment retry(String resourceSlug, UUID deploymentId, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(resourceSlug, ctx, MemberRole.EDITOR);
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        var source = deploymentRepository
                .findByIdAndResourceSlug(deploymentId, resourceSlug)
                .orElseThrow(() -> new NotFoundException("Deployment not found: " + deploymentId));
        boolean noImage = source.getImageRef() == null || source.getImageRef().isBlank();
        boolean noCommit =
                source.getSourceRef() == null || source.getSourceRef().isBlank();
        if (noImage && noCommit) {
            throw new ValidationException(
                    "Deployment " + deploymentId + " has neither imageRef nor sourceRef; cannot retry");
        }

        ImageSourceCacheEntity bound = imageSourceCacheRepository
                .findBySlug(resourceSlug)
                .orElseThrow(() -> new NotFoundException("Bound ImageSource not found for resource: " + resourceSlug));

        var fresh = new DeploymentEntity();
        fresh.setId(UuidV7.generate());
        fresh.setResourceSlug(resourceSlug);
        fresh.setEnvironmentId(env.getId());
        fresh.setImageSourceName(bound.getSlug());
        fresh.setImageSourceNamespace(bound.getNamespace());
        fresh.setSourceRef(source.getSourceRef());
        fresh.setImageRef(source.getImageRef());
        fresh.setLifecycle(BuildLifecycle.QUEUED);
        fresh.setTriggeredBy(TriggerType.MANUAL);
        fresh.setPrimary(false);
        fresh.setSourceDeploymentId(source.getId());
        var now = Instant.now();
        fresh.setCreatedAt(now);
        fresh.setUpdatedAt(now);
        deploymentRepository.save(fresh);

        deploymentNudger.retryBuild(
                bound.getNamespace(),
                bound.getSlug(),
                fresh.getId().toString(),
                source.getSourceRef(),
                source.getImageRef());

        auditLogService
                .audit(ctx, AuditAction.DEPLOYMENT_RETRIED)
                .target(AuditTargetType.RESOURCE, resourceSlug)
                .inWorkspace(workspaceId)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .change("sourceDeploymentId", "", deploymentId.toString())
                .change("newDeploymentId", "", fresh.getId().toString())
                .save();

        log.info(
                "Retried deployment {} for resource {} as new deployment {}",
                deploymentId,
                resourceSlug,
                fresh.getId());
        return ResourceEntityMapper.toApi(fresh, env.getSlug());
    }

    private static int clampWindow(Integer windowDays) {
        if (windowDays == null || windowDays <= 0) {
            return DEFAULT_STATS_WINDOW_DAYS;
        }
        return Math.min(MAX_STATS_WINDOW_DAYS, Math.max(MIN_STATS_WINDOW_DAYS, windowDays));
    }

    private static Collection<BuildLifecycle> lifecyclesFor(DeploymentLifecycleFilter filter) {
        return switch (filter) {
            case SUCCEEDED -> EnumSet.of(BuildLifecycle.ACTIVE, BuildLifecycle.BUILT);
            case FAILED -> EnumSet.of(BuildLifecycle.FAILED, BuildLifecycle.CANCELED);
            // ROLLBACK branch ignores the lifecycle list at the SQL level (the OR short-circuits)
            // but JPQL still chokes on an empty `IN :lifecycles` — pass any non-empty placeholder.
            case ROLLBACK -> EnumSet.of(BuildLifecycle.QUEUED);
            case ALL -> EnumSet.allOf(BuildLifecycle.class);
        };
    }

    private static List<DeploymentStatsBucket> padBuckets(
            List<DeploymentRepository.DeploymentStatsBucketRow> raw, Instant since, int windowDays) {
        Map<LocalDate, DeploymentRepository.DeploymentStatsBucketRow> byDay = new HashMap<>();
        for (var row : raw) {
            byDay.put(row.getDay().toLocalDate(), row);
        }
        LocalDate startDay = since.atOffset(ZoneOffset.UTC).toLocalDate();
        var out = new ArrayList<DeploymentStatsBucket>(windowDays);
        for (int i = 0; i < windowDays; i++) {
            LocalDate day = startDay.plusDays(i);
            var bucket = new DeploymentStatsBucket();
            bucket.setDay(day);
            var row = byDay.get(day);
            if (row == null) {
                bucket.setCount(0);
                bucket.setSuccess(0);
                bucket.setFailure(0);
            } else {
                bucket.setCount((int) row.getDeploys());
                bucket.setSuccess((int) row.getSuccess());
                bucket.setFailure((int) row.getFailure());
            }
            out.add(bucket);
        }
        return out;
    }

    private static DeploymentStatsTotals toTotals(DeploymentRepository.DeploymentStatsTotalsRow row) {
        var totals = new DeploymentStatsTotals();
        long total = row == null ? 0L : row.getDeploys();
        long success = row == null ? 0L : row.getSuccess();
        long failure = row == null ? 0L : row.getFailure();
        long rollback = row == null ? 0L : row.getRollback();
        totals.setTotal((int) total);
        totals.setSuccess((int) success);
        totals.setFailure((int) failure);
        totals.setRollback((int) rollback);
        if (total > 0) {
            // Two decimal places — clients can render `${rate.toFixed(1)}%` without losing precision.
            double rate = Math.round((double) success * 10000.0 / (double) total) / 100.0;
            totals.setSuccessRate(rate);
        }
        return totals;
    }
}

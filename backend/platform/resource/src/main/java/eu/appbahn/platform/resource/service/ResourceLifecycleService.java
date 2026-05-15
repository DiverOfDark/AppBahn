package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.AuditAction;
import eu.appbahn.platform.api.AuditTargetType;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.ConflictException;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.common.web.RetryOnConflict;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.model.MemberRole;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lifecycle operations on an existing Resource CRD: stop, start, restart. Separate from
 * {@link ResourceService} because none of these touch persistence or domain assignment —
 * they just flip a flag on the CR and let the operator reconcile.
 */
@Service
public class ResourceLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ResourceLifecycleService.class);

    private final ResourcePermissionHelper resourcePermissionHelper;
    private final ResourceCrdLookup crdLookup;
    private final ResourceCrdClient crdClient;
    private final AuditLogService auditLogService;

    public ResourceLifecycleService(
            ResourcePermissionHelper resourcePermissionHelper,
            ResourceCrdLookup crdLookup,
            ResourceCrdClient crdClient,
            AuditLogService auditLogService) {
        this.resourcePermissionHelper = resourcePermissionHelper;
        this.crdLookup = crdLookup;
        this.crdClient = crdClient;
        this.auditLogService = auditLogService;
    }

    @RetryOnConflict
    @Transactional
    public void stop(String slug, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        var existingCrd = crdLookup.get(slug, env.getSlug());
        if (existingCrd == null) {
            throw new NotFoundException("Resource CRD not found in Kubernetes: " + slug);
        }
        // Always enqueue the update — short-circuiting on the cached spec/status is unsafe under
        // concurrent stop/start: a pending start command in the operator's queue could land after
        // we no-op here, flipping the final state away from STOPPED. The operator's server-side
        // apply is idempotent, so a redundant enqueue is cheap; correctness wins over the audit
        // trail being a touch noisier.
        existingCrd.getSpec().setStopped(true);
        crdClient.update(existingCrd, null);
        log.info("Stopped Resource CRD: {}", slug);

        auditLogService
                .audit(ctx, AuditAction.RESOURCE_STOPPED)
                .target(AuditTargetType.RESOURCE, slug)
                .inWorkspace(workspaceId)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .save();
    }

    @RetryOnConflict
    @Transactional
    public void start(String slug, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        var existingCrd = crdLookup.get(slug, env.getSlug());
        if (existingCrd == null) {
            throw new NotFoundException("Resource CRD not found in Kubernetes: " + slug);
        }
        // Always enqueue the update — short-circuiting on the cached spec/status is unsafe under
        // concurrent stop/start: a pending stop command in the operator's queue could land after
        // we no-op here, flipping the final state to STOPPED. The operator's server-side apply
        // is idempotent, so a redundant enqueue is cheap; correctness wins over the audit trail
        // being a touch noisier.
        existingCrd.getSpec().setStopped(false);
        crdClient.update(existingCrd, null);
        log.info("Started Resource CRD: {}", slug);

        auditLogService
                .audit(ctx, AuditAction.RESOURCE_STARTED)
                .target(AuditTargetType.RESOURCE, slug)
                .inWorkspace(workspaceId)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .save();
    }

    @RetryOnConflict
    @Transactional
    public void restart(String slug, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
        var entity = resolved.entity();
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        if (ResourcePhase.READY != entity.getStatus()) {
            throw new ConflictException("Resource must be READY to restart, current status: " + entity.getStatus());
        }

        var existingCrd = crdLookup.get(slug, env.getSlug());
        if (existingCrd == null) {
            throw new NotFoundException("Resource CRD not found in Kubernetes: " + slug);
        }
        // crdLookup reads from resource_cache, which doesn't carry restartGeneration —
        // so reading the current value and incrementing always yielded `1`, and a
        // second restart would server-side-apply an unchanged spec (no metadata.generation
        // bump → no informer event → no ACTIVATING). Use a monotonically-increasing
        // timestamp instead; the reconciler only checks inequality vs the prior
        // observedRestartGeneration, and any new value triggers the re-roll path.
        existingCrd.getSpec().setRestartGeneration(System.currentTimeMillis());
        crdClient.update(existingCrd, null);
        log.info("Restarted Resource CRD: {}", slug);

        auditLogService
                .audit(ctx, AuditAction.RESOURCE_RESTARTED)
                .target(AuditTargetType.RESOURCE, slug)
                .inWorkspace(workspaceId)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .save();
    }
}

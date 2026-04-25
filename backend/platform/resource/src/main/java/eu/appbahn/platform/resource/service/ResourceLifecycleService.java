package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.AuditAction;
import eu.appbahn.platform.api.AuditTargetType;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.ConflictException;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.security.AuthContext;
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

    @Transactional
    public void stop(String slug, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        // Gate on spec.stopped — authoritative; cache status is a proxy that breaks mid-transition.
        var existingCrd = crdLookup.get(slug, env.getSlug());
        if (existingCrd == null) {
            throw new NotFoundException("Resource CRD not found in Kubernetes: " + slug);
        }
        if (Boolean.TRUE.equals(existingCrd.getSpec().getStopped())) {
            return;
        }
        existingCrd.getSpec().setStopped(true);
        crdClient.update(existingCrd);
        log.info("Stopped Resource CRD: {}", slug);

        auditLogService
                .audit(ctx, AuditAction.RESOURCE_STOPPED)
                .target(AuditTargetType.RESOURCE, slug)
                .inWorkspace(workspaceId)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .save();
    }

    @Transactional
    public void start(String slug, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        var existingCrd = crdLookup.get(slug, env.getSlug());
        if (existingCrd == null) {
            throw new NotFoundException("Resource CRD not found in Kubernetes: " + slug);
        }
        // Reconcile against status: spec.stopped can race ahead of the operator under concurrent
        // stop/start. If the resource is still STOPPED, write spec.stopped=false even when the
        // spec already shows it — operator reconciliation is idempotent.
        boolean specSaysStopped = Boolean.TRUE.equals(existingCrd.getSpec().getStopped());
        boolean statusSaysStopped =
                existingCrd.getStatus() != null && existingCrd.getStatus().getPhase() == ResourcePhase.STOPPED;
        if (!specSaysStopped && !statusSaysStopped) {
            return;
        }
        existingCrd.getSpec().setStopped(false);
        crdClient.update(existingCrd);
        log.info("Started Resource CRD: {}", slug);

        auditLogService
                .audit(ctx, AuditAction.RESOURCE_STARTED)
                .target(AuditTargetType.RESOURCE, slug)
                .inWorkspace(workspaceId)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .save();
    }

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
        existingCrd.getSpec().setDeploymentRevision(UUID.randomUUID().toString());
        crdClient.update(existingCrd);
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

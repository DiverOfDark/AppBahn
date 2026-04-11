package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.model.Deployment;
import eu.appbahn.platform.api.model.PagedDeploymentResponse;
import eu.appbahn.platform.api.model.TriggerDeploymentRequest;
import eu.appbahn.platform.api.model.TriggerDeploymentResponse;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.common.util.PagedResponseUtil;
import eu.appbahn.platform.common.util.PaginationUtil;
import eu.appbahn.platform.resource.entity.DeploymentEntity;
import eu.appbahn.platform.resource.repository.DeploymentRepository;
import eu.appbahn.platform.workspace.service.EnvironmentLookupService;
import eu.appbahn.platform.workspace.service.NamespaceService;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.model.MemberRole;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);

    private final DeploymentRepository deploymentRepository;
    private final ResourcePermissionHelper resourcePermissionHelper;
    private final EnvironmentLookupService environmentLookupService;
    private final AuditLogService auditLogService;
    private final NamespaceService namespaceService;
    private final ResourceCrdClient crdClient;
    private final EntityManager entityManager;

    public DeploymentService(
            DeploymentRepository deploymentRepository,
            ResourcePermissionHelper resourcePermissionHelper,
            EnvironmentLookupService environmentLookupService,
            AuditLogService auditLogService,
            NamespaceService namespaceService,
            ResourceCrdClient crdClient,
            EntityManager entityManager) {
        this.deploymentRepository = deploymentRepository;
        this.resourcePermissionHelper = resourcePermissionHelper;
        this.environmentLookupService = environmentLookupService;
        this.auditLogService = auditLogService;
        this.namespaceService = namespaceService;
        this.crdClient = crdClient;
        this.entityManager = entityManager;
    }

    @Transactional
    public TriggerDeploymentResponse trigger(String resourceSlug, TriggerDeploymentRequest req, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(resourceSlug, ctx, MemberRole.EDITOR);
        var resource = resolved.entity();
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        // Acquire advisory lock on resource slug to prevent concurrent deployment triggers
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(CAST(:slug AS TEXT)))")
                .setParameter("slug", resourceSlug)
                .getSingleResult();

        // Resolve image from resource config
        String imageRef = null;
        String sourceRef = req.getSourceRef();
        ResourceConfig resConfig = resource.getConfig();
        if (resConfig != null && resConfig.getSource() instanceof eu.appbahn.shared.crd.DockerSource dockerSource) {
            String image = dockerSource.getImage();
            String tag = dockerSource.getTag() != null ? dockerSource.getTag() : "latest";
            if (image != null) {
                imageRef = image + ":" + tag;
                if (sourceRef == null) {
                    sourceRef = image + ":" + tag;
                }
            }
        }

        // Check for duplicate deployment — if sourceRef matches the current primary, skip
        if (sourceRef != null) {
            var currentPrimary = deploymentRepository.findByResourceSlugAndPrimaryTrue(resourceSlug);
            if (currentPrimary.isPresent()
                    && sourceRef.equals(currentPrimary.get().getSourceRef())) {
                var response = new TriggerDeploymentResponse();
                response.setDeploymentId(currentPrimary.get().getId());
                response.setStatus(TriggerDeploymentResponse.StatusEnum.DUPLICATE);
                log.info("Deployment skipped for resource {} — sourceRef unchanged: {}", resourceSlug, sourceRef);
                return response;
            }
        }

        // Enforce build concurrency: at most 1 building + 1 queued per resource.
        // Delete any previously queued deployments — newest trigger wins.
        deploymentRepository.deleteByResourceSlugAndStatus(resourceSlug, eu.appbahn.shared.crd.DeploymentStatus.QUEUED);

        // Create deployment record (NOT primary — becomes primary only on SUCCEEDED)
        var entity = new DeploymentEntity();
        entity.setResourceSlug(resourceSlug);
        entity.setEnvironmentId(env.getId());
        entity.setSourceRef(sourceRef);
        entity.setImageRef(imageRef);
        entity.setTriggeredBy(TriggerType.MANUAL);
        // Docker sources skip QUEUED (no build step) and go straight to DEPLOYING
        if (resConfig != null
                && (resConfig.getSource() instanceof eu.appbahn.shared.crd.DockerSource
                        || resConfig.getSource() == null)) {
            entity.setStatus(eu.appbahn.shared.crd.DeploymentStatus.DEPLOYING);
        } else {
            entity.setStatus(eu.appbahn.shared.crd.DeploymentStatus.QUEUED);
        }
        entity.setPrimary(false);
        deploymentRepository.save(entity);

        // Update the Resource CRD to trigger operator reconciliation.
        // Setting deploymentRevision on the spec changes the pod template annotation,
        // which causes K8s to roll out new pods.
        String namespace = namespaceService.computeNamespace(env.getSlug());
        var existingCrd = crdClient.get(resourceSlug, namespace);
        if (existingCrd != null) {
            existingCrd.getSpec().setDeploymentRevision(entity.getId().toString());
            crdClient.update(existingCrd);
            log.info("Updated CRD deploymentRevision for resource {}", resourceSlug);
        }

        // Audit
        auditLogService.log(
                ctx,
                "deployment.triggered",
                "deployment",
                entity.getId().toString(),
                workspaceId,
                Map.of("resourceSlug", Map.of("old", "", "new", resourceSlug)));

        log.info("Triggered deployment {} for resource {}", entity.getId(), resourceSlug);

        var response = new TriggerDeploymentResponse();
        response.setDeploymentId(entity.getId());
        response.setStatus(
                entity.getStatus() == eu.appbahn.shared.crd.DeploymentStatus.DEPLOYING
                        ? TriggerDeploymentResponse.StatusEnum.DEPLOYING
                        : TriggerDeploymentResponse.StatusEnum.QUEUED);
        return response;
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
    public PagedDeploymentResponse list(String resourceSlug, Integer page, Integer size, String sort, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(resourceSlug, ctx, MemberRole.VIEWER);
        var env = resolved.env();

        var pageable = PaginationUtil.toPageable(page, size, sort, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = deploymentRepository.findByResourceSlug(resourceSlug, pageable);

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
}

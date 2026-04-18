package eu.appbahn.platform.resource.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.model.CreateResourceRequest;
import eu.appbahn.platform.api.model.PagedResourceResponse;
import eu.appbahn.platform.api.model.Resource;
import eu.appbahn.platform.api.model.ResourceCreatedResponse;
import eu.appbahn.platform.api.model.UpdateResourceRequest;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.ConflictException;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.exception.ValidationException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.common.util.PagedResponseUtil;
import eu.appbahn.platform.common.util.PaginationUtil;
import eu.appbahn.platform.resource.entity.DeploymentEntity;
import eu.appbahn.platform.resource.entity.ResourceCacheEntity;
import eu.appbahn.platform.resource.repository.DeploymentRepository;
import eu.appbahn.platform.resource.repository.ResourceCacheRepository;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.platform.workspace.service.EnvironmentLookupService;
import eu.appbahn.platform.workspace.service.NamespaceService;
import eu.appbahn.platform.workspace.service.PermissionService;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceSpec;
import eu.appbahn.shared.model.MemberRole;
import eu.appbahn.shared.util.SlugGenerator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ResourceService {

    private static final Logger log = LoggerFactory.getLogger(ResourceService.class);

    private final ResourceCacheRepository resourceCacheRepository;
    private final DeploymentRepository deploymentRepository;
    private final EnvironmentRepository environmentRepository;
    private final EnvironmentLookupService environmentLookupService;
    private final PermissionService permissionService;
    private final ResourcePermissionHelper resourcePermissionHelper;
    private final AuditLogService auditLogService;
    private final QuotaService quotaService;
    private final LicenseService licenseService;
    private final NamespaceService namespaceService;
    private final ResourceCrdClient crdClient;
    private final ObjectMapper objectMapper;
    private final String baseDomain;
    private final TransactionTemplate compensationTxTemplate;

    public ResourceService(
            ResourceCacheRepository resourceCacheRepository,
            DeploymentRepository deploymentRepository,
            EnvironmentRepository environmentRepository,
            EnvironmentLookupService environmentLookupService,
            PermissionService permissionService,
            ResourcePermissionHelper resourcePermissionHelper,
            AuditLogService auditLogService,
            QuotaService quotaService,
            LicenseService licenseService,
            NamespaceService namespaceService,
            ResourceCrdClient crdClient,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @org.springframework.beans.factory.annotation.Value("${platform.base-domain:appbahn.local}")
                    String baseDomain) {
        this.resourceCacheRepository = resourceCacheRepository;
        this.deploymentRepository = deploymentRepository;
        this.environmentRepository = environmentRepository;
        this.environmentLookupService = environmentLookupService;
        this.permissionService = permissionService;
        this.resourcePermissionHelper = resourcePermissionHelper;
        this.auditLogService = auditLogService;
        this.quotaService = quotaService;
        this.licenseService = licenseService;
        this.namespaceService = namespaceService;
        this.crdClient = crdClient;
        this.objectMapper = objectMapper;
        this.baseDomain = baseDomain;
        // REQUIRES_NEW: the afterCommit hook fires after the outer transaction is closed.
        this.compensationTxTemplate = new TransactionTemplate(transactionManager);
        this.compensationTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public ResourceCreatedResponse create(CreateResourceRequest req, AuthContext ctx) {
        if (!Labels.RESOURCE_TYPE_DEPLOYMENT.equals(req.getType())) {
            throw new ValidationException("Unknown resource type: " + req.getType());
        }

        var env = environmentRepository
                .findBySlug(req.getEnvironmentSlug())
                .orElseThrow(() -> new NotFoundException("Environment not found: " + req.getEnvironmentSlug()));

        permissionService.requireEnvironmentRole(ctx, env.getId(), MemberRole.EDITOR);

        ResourceConfig resourceConfig = objectMapper.convertValue(req.getConfig(), ResourceConfig.class);

        // checkQuota()'s advisory lock is held until commit.
        quotaService.checkQuota(env.getId(), null, resourceConfig);
        licenseService.checkLicense();

        String slug = SlugGenerator.generate(req.getName());

        assignDomain(resourceConfig, slug);

        UUID workspaceId = environmentLookupService.getWorkspaceId(env);

        var crd = new ResourceCrd();
        crd.getMetadata().setName(slug);
        crd.getMetadata().setNamespace(namespaceService.computeNamespace(env.getSlug()));
        crd.getMetadata().setLabels(Map.of(Labels.ENVIRONMENT_SLUG_KEY, env.getSlug()));

        var spec = new ResourceSpec();
        spec.setType(req.getType());
        spec.setName(req.getName());
        spec.setEnvironmentId(env.getId().toString());
        spec.setProjectId(env.getProjectId().toString());
        spec.setWorkspaceId(workspaceId.toString());
        spec.setConfig(resourceConfig);

        List<ResourceSpec.ResourceLink> resourceLinks = List.of();
        if (req.getLinks() != null && !req.getLinks().isEmpty()) {
            validateLinks(req.getLinks(), slug);
            resourceLinks = req.getLinks();
        }
        spec.setLinks(resourceLinks);
        crd.setSpec(spec);

        // Upsert a minimal cache entry now so concurrent creates see accurate license count and
        // the FK from DeploymentEntity holds. Links/statusDetail cleared so a resurrected slug
        // doesn't inherit the prior incarnation's state. Operator overwrites on first sync.
        var now = java.time.Instant.now();
        var cacheEntry = resourceCacheRepository.findBySlug(slug).orElseGet(ResourceCacheEntity::new);
        boolean isNew = cacheEntry.getSlug() == null;
        cacheEntry.setSlug(slug);
        cacheEntry.setEnvironmentId(env.getId());
        cacheEntry.setName(req.getName());
        cacheEntry.setType(req.getType());
        cacheEntry.setConfig(resourceConfig);
        cacheEntry.setLinks(resourceLinks);
        cacheEntry.setStatusDetail(null);
        cacheEntry.setStatus(ResourcePhase.PENDING);
        cacheEntry.setLastSyncedAt(now);
        if (isNew) {
            cacheEntry.setCreatedAt(now);
        }
        cacheEntry.setUpdatedAt(now);
        resourceCacheRepository.save(cacheEntry);

        DeploymentEntity initialDeployment = null;
        if (resourceConfig.getSource() instanceof eu.appbahn.shared.crd.DockerSource dockerSource
                && dockerSource.getImage() != null) {
            String tag = dockerSource.getTag() != null ? dockerSource.getTag() : "latest";
            String imageRef = dockerSource.getImage() + ":" + tag;

            initialDeployment = new DeploymentEntity();
            initialDeployment.setResourceSlug(slug);
            initialDeployment.setEnvironmentId(env.getId());
            initialDeployment.setSourceRef(imageRef);
            initialDeployment.setImageRef(imageRef);
            initialDeployment.setTriggeredBy(TriggerType.MANUAL);
            initialDeployment.setStatus(eu.appbahn.shared.crd.DeploymentStatus.DEPLOYING);
            initialDeployment.setPrimary(false);
            deploymentRepository.save(initialDeployment);

            spec.setDeploymentRevision(initialDeployment.getId().toString());
        }

        // Defer CRD create until after commit so the operator's sync callback sees the cache row
        // (UPDATE path) instead of racing us with a duplicate INSERT. On CRD failure, the
        // compensation marks the cache row ERROR rather than leaving a ghost row.
        final var crdToCreate = crd;
        runAfterCommit(() -> {
            try {
                crdClient.create(crdToCreate);
                log.info(
                        "Created Resource CRD: {} in namespace {}",
                        slug,
                        crdToCreate.getMetadata().getNamespace());
            } catch (RuntimeException ex) {
                log.error(
                        "Failed to create Resource CRD {} in namespace {} — marking cache row ERROR",
                        slug,
                        crdToCreate.getMetadata().getNamespace(),
                        ex);
                markCacheRowError(slug, "Failed to create Kubernetes resource: " + ex.getMessage());
            }
        });

        auditLogService.log(
                ctx,
                "resource.created",
                "resource",
                slug,
                workspaceId,
                Map.of(
                        "name", Map.of("old", "", "new", req.getName()),
                        "type", Map.of("old", "", "new", req.getType())));

        if (initialDeployment != null) {
            auditLogService.log(
                    ctx,
                    "deployment.triggered",
                    "deployment",
                    initialDeployment.getId().toString(),
                    workspaceId,
                    Map.of("resourceSlug", Map.of("old", "", "new", slug)));
        }

        var response = new ResourceCreatedResponse();
        response.setSlug(slug);
        response.setEnvironmentSlug(env.getSlug());
        return response;
    }

    @Transactional(readOnly = true)
    public Resource getBySlug(String slug, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.VIEWER);
        return ResourceEntityMapper.toApi(resolved.entity(), resolved.env().getSlug(), objectMapper);
    }

    @Transactional(readOnly = true)
    public PagedResourceResponse list(
            String environmentSlug, Integer page, Integer size, String sort, AuthContext ctx) {
        var env = environmentRepository
                .findBySlug(environmentSlug)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + environmentSlug));

        permissionService.requireEnvironmentRole(ctx, env.getId(), MemberRole.VIEWER);

        var pageable = PaginationUtil.toPageable(page, size, sort);
        Page<ResourceCacheEntity> result = resourceCacheRepository.findByEnvironmentId(env.getId(), pageable);

        return PagedResponseUtil.build(
                result,
                e -> ResourceEntityMapper.toApi(e, environmentSlug, objectMapper),
                new PagedResourceResponse(),
                PagedResourceResponse::setContent,
                PagedResourceResponse::setPage,
                PagedResourceResponse::setSize,
                PagedResourceResponse::setTotalElements,
                PagedResourceResponse::setTotalPages);
    }

    @Transactional
    public Resource update(String slug, UpdateResourceRequest req, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
        var entity = resolved.entity();
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        String oldName = entity.getName();
        ResourceConfig oldConfig = entity.getConfig();
        var oldLinks = entity.getLinks();

        // merge() returns a deep copy; existing is never mutated.
        ResourceConfig mergedConfig = null;
        if (req.getConfig() != null) {
            ResourceConfig existingConfig = entity.getConfig();
            JsonNode patchNode = objectMapper.valueToTree(req.getConfig());

            if (patchNode.has("source")) {
                ResourceConfigMerger.checkImmutableSourceType(existingConfig, patchNode.get("source"));
            }

            mergedConfig = ResourceConfigMerger.merge(existingConfig, patchNode, objectMapper);
            assignDomain(mergedConfig, slug);

            if (ResourceConfigMerger.hasHostingChange(existingConfig, mergedConfig)) {
                quotaService.checkQuota(env.getId(), slug, mergedConfig);
            }
        }

        List<ResourceSpec.ResourceLink> mergedLinks = null;
        if (req.getLinks() != null && !req.getLinks().isEmpty()) {
            validateLinks(req.getLinks(), slug);
            mergedLinks = req.getLinks();
        }

        // Patch the CRD on Kubernetes
        var existingCrd = getCrd(slug, env.getSlug());
        if (existingCrd != null) {
            if (req.getName() != null) {
                existingCrd.getSpec().setName(req.getName());
            }
            if (mergedConfig != null) {
                existingCrd.getSpec().setConfig(mergedConfig);
            }
            if (mergedLinks != null) {
                existingCrd.getSpec().setLinks(mergedLinks);
            }
            crdClient.update(existingCrd);
            log.info("Updated Resource CRD: {}", slug);
        } else {
            log.warn("CRD not found for resource {} during update — K8s state may diverge", slug);
        }

        if (req.getName() != null) {
            entity.setName(req.getName());
        }
        if (mergedConfig != null) {
            entity.setConfig(mergedConfig);
        }
        if (mergedLinks != null) {
            entity.setLinks(mergedLinks);
        }
        resourceCacheRepository.save(entity);

        var diff = new java.util.HashMap<String, Object>();
        if (req.getName() != null && !req.getName().equals(oldName)) {
            diff.put("name", Map.of("old", oldName, "new", req.getName()));
        }
        if (mergedConfig != null) {
            diff.put(
                    "config",
                    Map.of("old", objectMapper.valueToTree(oldConfig), "new", objectMapper.valueToTree(mergedConfig)));
        }
        if (mergedLinks != null) {
            diff.put("links", Map.of("old", oldLinks != null ? oldLinks : List.of(), "new", mergedLinks));
        }
        auditLogService.log(ctx, "resource.updated", "resource", slug, workspaceId, diff.isEmpty() ? null : diff);

        return ResourceEntityMapper.toApi(entity, env.getSlug(), objectMapper);
    }

    @Transactional
    public void delete(String slug, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
        var entity = resolved.entity();
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        var dependents = resourceCacheRepository.findByLinkedResourceSlug(slug);
        var dependentSlugs = dependents.stream()
                .filter(r -> !r.getSlug().equals(slug))
                .map(ResourceCacheEntity::getSlug)
                .toList();
        if (!dependentSlugs.isEmpty()) {
            throw new ConflictException(
                    "resource_has_dependents", "Cannot delete resource: other resources depend on it", dependentSlugs);
        }

        long deploymentCount = deploymentRepository.countByResourceSlug(slug);

        // Native DELETE (not the managed delete) so a concurrent operator sync doesn't abort
        // this transaction with StaleObjectStateException. Cascades to deployments via FK.
        resourceCacheRepository.deleteBySlugIfExists(slug);

        // Defer CRD delete until after commit; otherwise the operator's delete-sync races us
        // and our transaction tries to commit a stale DELETE. Delete by identity so fabric8
        // tolerates a missing target.
        final String namespace = namespaceService.computeNamespace(env.getSlug());
        runAfterCommit(() -> {
            try {
                crdClient.delete(slug, namespace);
                log.info("Deleted Resource CRD: {} in namespace {}", slug, namespace);
            } catch (RuntimeException ex) {
                log.error("Failed to delete Resource CRD {} in namespace {}", slug, namespace, ex);
            }
        });

        auditLogService.log(
                ctx,
                "resource.deleted",
                "resource",
                slug,
                workspaceId,
                Map.of("deploymentsDeleted", Map.of("old", deploymentCount, "new", 0)));
    }

    @Transactional
    public void stop(String slug, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        // Gate on spec.stopped (authoritative) rather than cache status (a proxy that breaks
        // under concurrency when the row is mid-transition).
        var existingCrd = getCrd(slug, env.getSlug());
        if (existingCrd == null) {
            throw new NotFoundException("Resource CRD not found in Kubernetes: " + slug);
        }
        if (Boolean.TRUE.equals(existingCrd.getSpec().getStopped())) {
            return;
        }

        existingCrd.getSpec().setStopped(true);
        crdClient.update(existingCrd);
        log.info("Stopped Resource CRD: {}", slug);

        // PENDING is transitional — pods still run until the operator sees readyReplicas == 0
        // and flips to STOPPED.
        resourceCacheRepository.updateStatusBySlug(slug, ResourcePhase.PENDING.name());

        auditLogService.log(ctx, "resource.stopped", "resource", slug, workspaceId, null);
    }

    @Transactional
    public void start(String slug, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        // See stop() for why we gate on spec, not cache.
        var existingCrd = getCrd(slug, env.getSlug());
        if (existingCrd == null) {
            throw new NotFoundException("Resource CRD not found in Kubernetes: " + slug);
        }
        if (!Boolean.TRUE.equals(existingCrd.getSpec().getStopped())) {
            return;
        }

        existingCrd.getSpec().setStopped(false);
        crdClient.update(existingCrd);
        log.info("Started Resource CRD: {}", slug);

        resourceCacheRepository.updateStatusBySlug(slug, ResourcePhase.PENDING.name());

        auditLogService.log(ctx, "resource.started", "resource", slug, workspaceId, null);
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

        var existingCrd = getCrd(slug, env.getSlug());
        if (existingCrd == null) {
            throw new NotFoundException("Resource CRD not found in Kubernetes: " + slug);
        }
        // Must be a UUID — public-api.yaml declares deploymentRevision as such.
        existingCrd.getSpec().setDeploymentRevision(UUID.randomUUID().toString());
        crdClient.update(existingCrd);
        log.info("Restarted Resource CRD: {}", slug);

        resourceCacheRepository.updateStatusBySlug(slug, ResourcePhase.RESTARTING.name());

        auditLogService.log(ctx, "resource.restarted", "resource", slug, workspaceId, null);
    }

    @Nullable
    private ResourceCrd getCrd(String slug, String envSlug) {
        String namespace = namespaceService.computeNamespace(envSlug);
        return crdClient.get(slug, namespace);
    }

    /**
     * If no synchronization is active (unit tests without a managed tx), runs inline instead
     * of registering — lets callers keep a single code path.
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /**
     * Compensation: flip an already-committed cache row to ERROR after its CRD op failed.
     * Secondary failures here are swallowed — we've already failed the primary operation.
     */
    private void markCacheRowError(String slug, String message) {
        try {
            compensationTxTemplate.executeWithoutResult(status -> {
                resourceCacheRepository.findBySlug(slug).ifPresent(entity -> {
                    entity.setStatus(ResourcePhase.ERROR);
                    var detail = entity.getStatusDetail() != null
                            ? entity.getStatusDetail()
                            : new eu.appbahn.shared.crd.ResourceStatus();
                    detail.setPhase(ResourcePhase.ERROR);
                    detail.setMessage(message);
                    entity.setStatusDetail(detail);
                    entity.setUpdatedAt(java.time.Instant.now());
                    resourceCacheRepository.save(entity);
                });
            });
        } catch (RuntimeException nested) {
            log.error("Compensation failed: could not mark {} as ERROR: {}", slug, nested.getMessage(), nested);
        }
    }

    /**
     * Primary domain: {@code {slug}.{baseDomain}}. Per-port domains for additional ingress
     * ports are planned tech debt; today we only set the primary.
     */
    private void assignDomain(ResourceConfig config, String slug) {
        for (var port : config.getIngressPorts()) {
            if (port.getDomain() == null) {
                port.setDomain(slug + "." + baseDomain);
            }
        }
    }

    private void validateLinks(java.util.List<ResourceSpec.ResourceLink> links, String currentSlug) {
        var slugs = new java.util.ArrayList<String>();
        for (var link : links) {
            if (link.getResource() == null || link.getResource().isBlank()) {
                throw new ValidationException("Link resource slug must not be empty");
            }
            if (link.getResource().equals(currentSlug)) {
                throw new ValidationException("Resource cannot link to itself");
            }
            slugs.add(link.getResource());
        }
        if (!slugs.isEmpty()) {
            var found = resourceCacheRepository.findAllById(slugs).stream()
                    .map(r -> r.getSlug())
                    .collect(java.util.stream.Collectors.toSet());
            for (var slug : slugs) {
                if (!found.contains(slug)) {
                    throw new ValidationException("Linked resource not found: " + slug);
                }
            }
        }
    }
}

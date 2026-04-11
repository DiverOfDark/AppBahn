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
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        // Quota + license checks run within the same transaction; the advisory lock acquired
        // inside checkQuota() is held until this transaction commits.
        quotaService.checkQuota(env.getId(), null, resourceConfig);
        licenseService.checkLicense();

        String slug = SlugGenerator.generate(req.getName());

        // Auto-generate domain for ingress-exposed ports
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
        crd.setSpec(spec);

        // Insert a minimal cache entry immediately so the license count is accurate
        // for concurrent creates. The operator will overwrite this on first sync.
        // Must be saved before deployment (FK constraint).
        var cacheEntry = new ResourceCacheEntity();
        cacheEntry.setSlug(slug);
        cacheEntry.setEnvironmentId(env.getId());
        cacheEntry.setName(req.getName());
        cacheEntry.setType(req.getType());
        cacheEntry.setConfig(resourceConfig);
        cacheEntry.setStatus(ResourcePhase.PENDING);
        var now = java.time.Instant.now();
        cacheEntry.setLastSyncedAt(now);
        cacheEntry.setCreatedAt(now);
        cacheEntry.setUpdatedAt(now);
        resourceCacheRepository.save(cacheEntry);

        // For Docker sources, create an initial deployment record so there's an audit trail
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

        crdClient.create(crd);
        log.info(
                "Created Resource CRD: {} in namespace {}",
                slug,
                crd.getMetadata().getNamespace());

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

        // Capture old values for audit diff
        String oldName = entity.getName();
        ResourceConfig oldConfig = entity.getConfig();
        var oldLinks = entity.getLinks();

        // Merge config changes (merge returns a deep copy; existing is never mutated)
        ResourceConfig mergedConfig = null;
        if (req.getConfig() != null) {
            ResourceConfig existingConfig = entity.getConfig();
            JsonNode patchNode = objectMapper.valueToTree(req.getConfig());

            if (patchNode.has("source")) {
                ResourceConfigMerger.checkImmutableSourceType(existingConfig, patchNode.get("source"));
            }

            mergedConfig = ResourceConfigMerger.merge(existingConfig, patchNode, objectMapper);

            // Auto-generate domain if update introduces an ingress port
            assignDomain(mergedConfig, slug);

            if (ResourceConfigMerger.hasHostingChange(existingConfig, mergedConfig)) {
                quotaService.checkQuota(env.getId(), slug, mergedConfig);
            }
        }

        // Links from the API are now the same type as CRD ResourceLink (via schema mapping)
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

        // Save merged config to cache entity before returning
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

        // Check for dependencies (other resources linking to this one)
        var dependents = resourceCacheRepository.findByLinkedResourceSlug(slug);
        var dependentSlugs = dependents.stream()
                .filter(r -> !r.getSlug().equals(slug))
                .map(ResourceCacheEntity::getSlug)
                .toList();
        if (!dependentSlugs.isEmpty()) {
            throw new ConflictException(
                    "resource_has_dependents", "Cannot delete resource: other resources depend on it", dependentSlugs);
        }

        // Delete CRD from Kubernetes
        var existingCrd = getCrd(slug, env.getSlug());
        if (existingCrd != null) {
            crdClient.delete(existingCrd);
            log.info("Deleted Resource CRD: {}", slug);
        }

        // Count deployments before cascade delete
        long deploymentCount = deploymentRepository.countByResourceSlug(slug);

        // Remove from cache (cascade-deletes deployments via FK)
        resourceCacheRepository.deleteById(slug);

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
        var entity = resolved.entity();
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        if (ResourcePhase.STOPPED == entity.getStatus()) {
            return; // Already stopped — idempotent no-op
        }

        var allowed =
                Set.of(ResourcePhase.READY, ResourcePhase.RESTARTING, ResourcePhase.DEGRADED, ResourcePhase.ERROR);
        if (!allowed.contains(entity.getStatus())) {
            throw new ConflictException(
                    "Resource must be in READY, RESTARTING, DEGRADED, or ERROR to stop, current status: "
                            + entity.getStatus());
        }

        // Update the CRD to set stopped = true
        var existingCrd = getCrd(slug, env.getSlug());
        if (existingCrd == null) {
            throw new NotFoundException("Resource CRD not found in Kubernetes: " + slug);
        }
        existingCrd.getSpec().setStopped(true);
        crdClient.update(existingCrd);
        log.info("Stopped Resource CRD: {}", slug);

        entity.setStatus(ResourcePhase.STOPPED);
        resourceCacheRepository.save(entity);

        auditLogService.log(ctx, "resource.stopped", "resource", slug, workspaceId, null);
    }

    @Transactional
    public void start(String slug, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
        var entity = resolved.entity();
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        // Start only from STOPPED
        if (ResourcePhase.STOPPED != entity.getStatus()) {
            throw new ConflictException("Resource must be STOPPED to start, current status: " + entity.getStatus());
        }

        // Update the CRD to set stopped = false
        var existingCrd = getCrd(slug, env.getSlug());
        if (existingCrd == null) {
            throw new NotFoundException("Resource CRD not found in Kubernetes: " + slug);
        }
        existingCrd.getSpec().setStopped(false);
        crdClient.update(existingCrd);
        log.info("Started Resource CRD: {}", slug);

        entity.setStatus(ResourcePhase.PENDING);
        resourceCacheRepository.save(entity);

        auditLogService.log(ctx, "resource.started", "resource", slug, workspaceId, null);
    }

    @Transactional
    public void restart(String slug, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
        var entity = resolved.entity();
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        // Restart only from READY
        if (ResourcePhase.READY != entity.getStatus()) {
            throw new ConflictException("Resource must be READY to restart, current status: " + entity.getStatus());
        }

        // Trigger restart by bumping deploymentRevision
        var existingCrd = getCrd(slug, env.getSlug());
        if (existingCrd == null) {
            throw new NotFoundException("Resource CRD not found in Kubernetes: " + slug);
        }
        existingCrd.getSpec().setDeploymentRevision(java.time.Instant.now().toString());
        crdClient.update(existingCrd);
        log.info("Restarted Resource CRD: {}", slug);

        entity.setStatus(ResourcePhase.RESTARTING);
        resourceCacheRepository.save(entity);

        auditLogService.log(ctx, "resource.restarted", "resource", slug, workspaceId, null);
    }

    /** Fetch a ResourceCrd from Kubernetes by slug and environment slug. Returns null if not found. */
    @Nullable
    private ResourceCrd getCrd(String slug, String envSlug) {
        String namespace = namespaceService.computeNamespace(envSlug);
        return crdClient.get(slug, namespace);
    }

    /**
     * Auto-generate the domain for resources with ingress-exposed ports.
     * Per spec: primary domain is {@code {slug}.{baseDomain}}, additional ingress ports
     * use {@code {slug}-{port}.{baseDomain}}.
     *
     * <p>For Sprint 5 (single ingress port), only the primary domain is set on hosting.
     * Multi-port per-port domains are tracked as tech debt.
     */
    private void assignDomain(ResourceConfig config, String slug) {
        for (var port : config.getIngressPorts()) {
            // Only set domain if not already provided (allow user override)
            if (port.getDomain() == null) {
                port.setDomain(slug + "." + baseDomain);
            }
        }
    }

    /** Validate that all linked resources exist and no self-links are present. */
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

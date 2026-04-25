package eu.appbahn.platform.resource.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.AuditAction;
import eu.appbahn.platform.api.AuditTargetType;
import eu.appbahn.platform.api.Resource;
import eu.appbahn.platform.api.resource.CreateResourceRequest;
import eu.appbahn.platform.api.resource.PagedResourceResponse;
import eu.appbahn.platform.api.resource.ResourceCreatedResponse;
import eu.appbahn.platform.api.resource.UpdateResourceRequest;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.ConflictException;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.exception.ValidationException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.common.util.PagedResponseUtil;
import eu.appbahn.platform.common.util.PaginationUtil;
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
import eu.appbahn.shared.crd.ResourceSpec;
import eu.appbahn.shared.model.MemberRole;
import eu.appbahn.shared.util.SlugGenerator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for Resource CRUD. Kubernetes is the source of truth; the operator is the only writer
 * to {@code resource_cache}. This service writes to Kubernetes (CRDs) synchronously and defers
 * the cache update to the operator's next sync.
 */
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
    private final ResourceCrdLookup crdLookup;
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
            ResourceCrdLookup crdLookup,
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
        this.crdLookup = crdLookup;
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

        // Pre-CRD gate: reject obviously over-quota/over-license requests before creating the CRD.
        // There's a small soft-overshoot window between this check and the operator's first sync.
        quotaService.checkQuota(env.getId(), null, resourceConfig);
        licenseService.checkLicense();

        String slug = SlugGenerator.generate(req.getName());
        assignDomain(resourceConfig, slug);

        UUID workspaceId = environmentLookupService.getWorkspaceId(env);

        List<ResourceSpec.LinkConfig> resourceLinks = List.of();
        if (req.getLinks() != null && !req.getLinks().isEmpty()) {
            validateLinks(req.getLinks(), slug);
            resourceLinks = req.getLinks();
        }

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
        spec.setLinks(resourceLinks);
        // Pre-generate a deploymentRevision so the operator's first status sync carries a stable
        // UUID — ResourceSyncService materialises the matching DeploymentEntity on first sight.
        spec.setDeploymentRevision(UUID.randomUUID().toString());
        crd.setSpec(spec);

        crdClient.create(crd);
        log.info(
                "Created Resource CRD: {} in namespace {}",
                slug,
                crd.getMetadata().getNamespace());

        auditLogService
                .audit(ctx, AuditAction.RESOURCE_CREATED)
                .target(AuditTargetType.RESOURCE, slug)
                .inWorkspace(workspaceId)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .change("name", "", req.getName())
                .change("type", "", req.getType())
                .save();

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

        ResourceConfig mergedConfig = null;
        if (req.getConfig() != null) {
            JsonNode patchNode = objectMapper.valueToTree(req.getConfig());
            if (patchNode.has("source")) {
                ResourceConfigMerger.checkImmutableSourceType(oldConfig, patchNode.get("source"));
            }
            mergedConfig = ResourceConfigMerger.merge(oldConfig, patchNode, objectMapper);
            assignDomain(mergedConfig, slug);
            if (ResourceConfigMerger.hasHostingChange(oldConfig, mergedConfig)) {
                quotaService.checkQuota(env.getId(), slug, mergedConfig);
            }
        }

        List<ResourceSpec.LinkConfig> mergedLinks = null;
        if (req.getLinks() != null && !req.getLinks().isEmpty()) {
            validateLinks(req.getLinks(), slug);
            mergedLinks = req.getLinks();
        }

        var existingCrd = crdLookup.get(slug, env.getSlug());
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

        // Reflect the merged state in the cache immediately so follow-up reads (including
        // DeploymentService.trigger picking up the new sourceRef) see the user's intent
        // without waiting on the operator's next sync. The operator's sync will overwrite
        // with the same values (both reads are from K8s, our merge and the operator's
        // reconcile converge).
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

        var auditBuilder = auditLogService
                .audit(ctx, AuditAction.RESOURCE_UPDATED)
                .target(AuditTargetType.RESOURCE, slug)
                .inWorkspace(workspaceId)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId());
        if (req.getName() != null && !req.getName().equals(oldName)) {
            auditBuilder.change("name", oldName, req.getName());
        }
        if (mergedConfig != null) {
            auditBuilder.change("config", toJsonString(oldConfig), toJsonString(mergedConfig));
        }
        if (mergedLinks != null) {
            auditBuilder.change(
                    "links", toJsonString(oldLinks != null ? oldLinks : List.of()), toJsonString(mergedLinks));
        }
        auditBuilder.save();

        return ResourceEntityMapper.toApi(entity, env.getSlug(), objectMapper);
    }

    @Transactional
    public void delete(String slug, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
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

        String namespace = namespaceService.computeNamespace(env.getSlug());
        crdClient.delete(slug, namespace);
        log.info("Deleted Resource CRD: {} in namespace {}", slug, namespace);

        auditLogService
                .audit(ctx, AuditAction.RESOURCE_DELETED)
                .target(AuditTargetType.RESOURCE, slug)
                .inWorkspace(workspaceId)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .change("deploymentsDeleted", deploymentCount, 0)
                .save();
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

    private void validateLinks(java.util.List<ResourceSpec.LinkConfig> links, String currentSlug) {
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
                    .map(ResourceCacheEntity::getSlug)
                    .collect(java.util.stream.Collectors.toSet());
            for (var slug : slugs) {
                if (!found.contains(slug)) {
                    throw new ValidationException("Linked resource not found: " + slug);
                }
            }
        }
    }

    private String toJsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}

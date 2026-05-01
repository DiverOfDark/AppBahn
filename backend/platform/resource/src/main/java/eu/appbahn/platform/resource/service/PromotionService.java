package eu.appbahn.platform.resource.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.AuditAction;
import eu.appbahn.platform.api.AuditTargetType;
import eu.appbahn.platform.api.TriggerType;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.exception.ValidationException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.common.web.RetryOnConflict;
import eu.appbahn.platform.resource.entity.DeploymentEntity;
import eu.appbahn.platform.resource.entity.ImageSourceCacheEntity;
import eu.appbahn.platform.resource.repository.DeploymentRepository;
import eu.appbahn.platform.resource.repository.ImageSourceCacheRepository;
import eu.appbahn.platform.workspace.service.NamespaceService;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.imagesource.BuildLifecycle;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourcePromotionSpec;
import eu.appbahn.shared.crd.imagesource.ImageSourceSpec;
import eu.appbahn.shared.crd.imagesource.ImageSourceType;
import eu.appbahn.shared.crd.imagesource.LatestArtifact;
import eu.appbahn.shared.model.MemberRole;
import eu.appbahn.shared.util.UuidV7;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements promote/rollback semantics by editing the bound ImageSource's
 * {@code spec.imageSource.pinnedDigest} (for promotion-chain) or {@code spec.image.ref} (for
 * pinned-image) and dispatching a one-sided {@code ApplyResourceBundle}. The operator picks up
 * the change, status updates flow back, K8s rolls.
 *
 * <p>Promotion model:
 *
 * <ul>
 *   <li>{@code POST /promote} with no digest → resolve the bound ImageSource's
 *       {@code spec.imageSource.upstream} → read upstream's {@code latestArtifact.imageRef} →
 *       set as the downstream's pinnedDigest.
 *   <li>{@code POST /promote} with explicit digest → set as pinnedDigest directly.
 *   <li>{@code POST /rollback} with deploymentId → look up the audit row → use its imageRef.
 *   <li>{@code POST /rollback} no body → use the previous successful (non-current) deployment.
 * </ul>
 *
 * <p>Type-specific semantics:
 *
 * <ul>
 *   <li>{@code type: imageSource} → set/unset {@code spec.imageSource.pinnedDigest}; flips
 *       {@code autoPromote=false} when pinning so subsequent upstream changes don't override.
 *   <li>{@code type: image} → set {@code spec.image.ref} to the target digest. {@code autoPromote}
 *       does not apply.
 *   <li>{@code type: git} → rejected with 422; callers should revert their commit.
 * </ul>
 */
@Service
public class PromotionService {

    private static final Logger log = LoggerFactory.getLogger(PromotionService.class);

    private final ResourcePermissionHelper resourcePermissionHelper;
    private final ImageSourceCacheRepository imageSourceCacheRepository;
    private final DeploymentRepository deploymentRepository;
    private final ResourceCrdLookup crdLookup;
    private final ResourceCrdClient crdClient;
    private final NamespaceService namespaceService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public PromotionService(
            ResourcePermissionHelper resourcePermissionHelper,
            ImageSourceCacheRepository imageSourceCacheRepository,
            DeploymentRepository deploymentRepository,
            ResourceCrdLookup crdLookup,
            ResourceCrdClient crdClient,
            NamespaceService namespaceService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.resourcePermissionHelper = resourcePermissionHelper;
        this.imageSourceCacheRepository = imageSourceCacheRepository;
        this.deploymentRepository = deploymentRepository;
        this.crdLookup = crdLookup;
        this.crdClient = crdClient;
        this.namespaceService = namespaceService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @RetryOnConflict
    @Transactional
    public void promote(String slug, String explicitDigest, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        ImageSourceCacheEntity boundImageSource = loadBoundImageSource(slug);
        ImageSourceSpec spec = boundImageSource.getSpec();
        rejectGitType(spec, "promote");

        String targetDigest;
        TriggerType trigger;
        if (explicitDigest != null && !explicitDigest.isBlank()) {
            targetDigest = explicitDigest;
            trigger = TriggerType.MANUAL;
        } else {
            targetDigest = resolveUpstreamLatestDigest(spec);
            trigger = TriggerType.AUTO_PROMOTION;
        }

        applyPin(boundImageSource, env.getSlug(), spec, targetDigest);
        recordPromotion(slug, env.getId(), boundImageSource, targetDigest, trigger);
        log.info("Promoted resource {} to digest {}", slug, targetDigest);

        auditLogService
                .audit(ctx, AuditAction.RESOURCE_UPDATED)
                .target(AuditTargetType.RESOURCE, slug)
                .inWorkspace(workspaceId)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .change("promotedDigest", "", targetDigest)
                .save();
    }

    @RetryOnConflict
    @Transactional
    public void rollback(String slug, UUID deploymentId, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        ImageSourceCacheEntity boundImageSource = loadBoundImageSource(slug);
        ImageSourceSpec spec = boundImageSource.getSpec();
        rejectGitType(spec, "rollback");

        DeploymentEntity target = deploymentId != null
                ? deploymentRepository
                        .findByIdAndResourceSlug(deploymentId, slug)
                        .orElseThrow(() -> new NotFoundException("Deployment not found: " + deploymentId))
                : findPreviousSuccessfulDeployment(slug);

        if (target.getImageRef() == null || target.getImageRef().isBlank()) {
            throw new ValidationException("Deployment " + target.getId() + " has no imageRef; cannot roll back");
        }
        String targetDigest = target.getImageRef();
        applyPin(boundImageSource, env.getSlug(), spec, targetDigest);
        recordPromotion(slug, env.getId(), boundImageSource, targetDigest, TriggerType.ROLLBACK);
        log.info("Rolled back resource {} to deployment {} (digest {})", slug, target.getId(), targetDigest);

        auditLogService
                .audit(ctx, AuditAction.RESOURCE_UPDATED)
                .target(AuditTargetType.RESOURCE, slug)
                .inWorkspace(workspaceId)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .change("rolledBackTo", "", target.getId().toString())
                .change("rolledBackDigest", "", targetDigest)
                .save();
    }

    /**
     * Record a fresh deployment audit row capturing the digest the resource is being moved to.
     * Promote and rollback both reuse an existing artifact (no build runs) — but the operations
     * still represent a state change worth tracking, and rollback's "previous deployment" lookup
     * relies on a row existing for each prior pin. Lifecycle starts at {@code ACTIVATING};
     * {@code ReleaseLifecycleEmitter} on the operator advances it to {@code ACTIVE} once the
     * rollout settles.
     */
    private void recordPromotion(
            String slug, UUID envId, ImageSourceCacheEntity bound, String digest, TriggerType trigger) {
        var dep = new DeploymentEntity();
        dep.setId(UuidV7.generate());
        dep.setResourceSlug(slug);
        dep.setEnvironmentId(envId);
        dep.setImageSourceName(bound.getSlug());
        dep.setImageSourceNamespace(bound.getNamespace());
        dep.setImageRef(digest);
        dep.setLifecycle(BuildLifecycle.ACTIVATING);
        dep.setTriggeredBy(trigger);
        dep.setPrimary(false);
        var now = Instant.now();
        dep.setCreatedAt(now);
        dep.setUpdatedAt(now);
        deploymentRepository.save(dep);
    }

    private ImageSourceCacheEntity loadBoundImageSource(String slug) {
        // Bound ImageSource shares its name with the Resource (per ResourceService.create).
        return imageSourceCacheRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Bound ImageSource not found for resource: " + slug));
    }

    private static void rejectGitType(ImageSourceSpec spec, String op) {
        if (spec == null) {
            throw new ValidationException("ImageSource spec is missing");
        }
        if (spec.getType() == ImageSourceType.GIT) {
            throw new ValidationException("Cannot " + op
                    + " a git-type ImageSource — git ImageSources roll forward; revert the source commit instead");
        }
    }

    /**
     * For {@code type: imageSource}, returns the upstream's current latestArtifact digest
     * (which the operator/broker has already mirrored into the upstream's image_source_cache row).
     * For {@code type: image}, "no digest" means there's no upstream — caller must supply one.
     */
    private String resolveUpstreamLatestDigest(ImageSourceSpec spec) {
        if (spec.getType() != ImageSourceType.IMAGE_SOURCE) {
            throw new ValidationException(
                    "promote without explicit digest requires type=imageSource (with upstream); supply a digest for type=image");
        }
        ImageSourcePromotionSpec promo = spec.getImageSource();
        if (promo == null || promo.getUpstream() == null) {
            throw new ValidationException("type=imageSource is missing imageSource.upstream");
        }
        // The upstream lives in {namespace = upstream.namespace}; its slug is the upstream.name
        // (the cache key is the slug, which equals the CR name).
        String upstreamSlug = promo.getUpstream().getName();
        ImageSourceCacheEntity upstream = imageSourceCacheRepository
                .findBySlug(upstreamSlug)
                .orElseThrow(() -> new NotFoundException("Upstream ImageSource not found: " + upstreamSlug));
        if (upstream.getStatus() == null) {
            throw new ValidationException("Upstream " + upstreamSlug + " has no status yet");
        }
        LatestArtifact artifact = upstream.getStatus().getLatestArtifact();
        if (artifact == null
                || artifact.getImageRef() == null
                || artifact.getImageRef().isBlank()) {
            throw new ValidationException("Upstream " + upstreamSlug + " has no latestArtifact yet");
        }
        return artifact.getImageRef();
    }

    private DeploymentEntity findPreviousSuccessfulDeployment(String slug) {
        // "Previous" = most recent deployment row with a different imageRef than the current
        // primary's. Sorted by createdAt desc; the platform's BuildLifecycleHandler doesn't
        // demote prior primaries, so a strict "non-primary" filter would skip them too.
        var page = deploymentRepository.findByResourceSlug(
                slug,
                org.springframework.data.domain.PageRequest.of(
                        0,
                        20,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));
        DeploymentEntity current = null;
        for (var dep : page.getContent()) {
            if (dep.getImageRef() == null || dep.getImageRef().isBlank()) {
                continue;
            }
            if (current == null) {
                current = dep;
                continue;
            }
            if (!dep.getImageRef().equals(current.getImageRef())) {
                return dep;
            }
        }
        throw new ValidationException("No previous successful deployment found for resource: " + slug);
    }

    /**
     * Mutate {@code spec} to pin {@code digest}, then dispatch an {@code ApplyResourceBundle}
     * that updates the ImageSource's spec on the target cluster. For {@code type: imageSource}
     * this also flips {@code autoPromote=false} so the operator no longer follows the upstream
     * automatically (otherwise the next upstream change would clobber the pin immediately).
     */
    private void applyPin(
            ImageSourceCacheEntity boundImageSource, String envSlug, ImageSourceSpec spec, String digest) {
        ImageSourceSpec mutated = objectMapper.convertValue(spec, ImageSourceSpec.class);
        if (mutated.getType() == ImageSourceType.IMAGE_SOURCE) {
            ImageSourcePromotionSpec promo = mutated.getImageSource();
            if (promo == null) {
                promo = new ImageSourcePromotionSpec();
                mutated.setImageSource(promo);
            }
            promo.setAutoPromote(false);
            promo.setPinnedDigest(digest);
        } else if (mutated.getType() == ImageSourceType.IMAGE) {
            if (mutated.getImage() == null) {
                throw new ValidationException("type=image but spec.image is missing");
            }
            mutated.getImage().setRef(digest);
        }

        ImageSourceCrd crd = new ImageSourceCrd();
        var meta = new ObjectMeta();
        meta.setName(boundImageSource.getSlug());
        meta.setNamespace(namespaceService.computeNamespace(envSlug));
        meta.setLabels(Map.of(Labels.ENVIRONMENT_SLUG_KEY, envSlug));
        crd.setMetadata(meta);
        crd.setSpec(mutated);

        // Dispatch as ImageSource-only bundle (resource=null). The operator's command handler
        // applies the ImageSource alone — the bound Resource is unchanged.
        crdClient.update(buildResourcePassthrough(boundImageSource.getSlug(), envSlug), crd);
    }

    /**
     * The {@link ResourceCrdClient#update} contract takes a Resource + optional ImageSource; for
     * pin operations we pass through the existing Resource unchanged so the dispatcher has the
     * env-slug label needed for cluster routing.
     */
    private eu.appbahn.shared.crd.ResourceCrd buildResourcePassthrough(String slug, String envSlug) {
        var existing = crdLookup.get(slug, envSlug);
        if (existing == null) {
            throw new NotFoundException("Resource CRD not found: " + slug);
        }
        return existing;
    }
}

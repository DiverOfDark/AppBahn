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
import eu.appbahn.shared.crd.PinnedRelease;
import eu.appbahn.shared.crd.ResourceCrd;
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
 * Implements promote / rollback / unpin semantics. Two distinct layers:
 *
 * <ul>
 *   <li><b>Promote</b> for {@code type=imageSource} sets {@code spec.imageSource.pinnedDigest}
 *       and for {@code type=image} sets {@code spec.image.ref} (IS-level pin, visible to all
 *       downstream consumers — controls the promotion gate between environments). For
 *       {@code type=git} there is no IS-level pin slot; promote uses the Resource-level
 *       {@code spec.pinnedRelease} instead, requiring an explicit digest that matches a
 *       historical deployment for that Resource.
 *   <li><b>Rollback / unpin</b> always edit the Resource's {@code spec.pinnedRelease}. Pins the
 *       Resource to a previous deployment's full snapshot (digest + run command + commit) and
 *       lets the operator re-roll without rebuilding — Vercel/Railway/Heroku-style fast rollback.
 *       Independent of ImageSource type; works for {@code git} too (the load-bearing case).
 * </ul>
 *
 * <p>The two layers compose: a staging Resource can have its {@code pinnedRelease} set even
 * while its {@code type=imageSource} ImageSource is auto-mirroring upstream. The pin wins —
 * the Resource stays on the historical artifact until cleared.
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
        if (spec == null) {
            throw new ValidationException("ImageSource spec is missing");
        }

        // Type-specific paths:
        //   git         → IS has no pin slot; pin Resource.spec.pinnedRelease to a historical
        //                 artifact instead (same mechanism rollback uses since PR8).
        //   image       → write spec.image.ref on the IS; the IS itself becomes the user's pin.
        //   imageSource → write spec.imageSource.pinnedDigest on the IS; downstream stops
        //                 tracking upstream's latestArtifact.
        if (spec.getType() == ImageSourceType.GIT) {
            promoteGitByPinningResource(slug, resolved, boundImageSource, explicitDigest, ctx);
            return;
        }

        String targetDigest;
        TriggerType trigger;
        if (explicitDigest != null && !explicitDigest.isBlank()) {
            targetDigest = explicitDigest;
            trigger = TriggerType.MANUAL;
        } else {
            targetDigest = resolveUpstreamLatestDigest(spec);
            trigger = TriggerType.AUTO_PROMOTION;
        }

        applyImageSourcePin(boundImageSource, env.getSlug(), spec, targetDigest);
        recordDeploymentRow(slug, env.getId(), boundImageSource, targetDigest, trigger);
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

    /**
     * Git ImageSources have no IS-level pin slot, so promote uses the same Resource-level pin
     * mechanism rollback uses (PR8). Requires an explicit digest that matches a historical
     * deployment for this Resource — auto-promote on git is the build pipeline's job, and an
     * arbitrary external digest belongs on a {@code type: image} ImageSource.
     */
    private void promoteGitByPinningResource(
            String slug,
            ResourcePermissionHelper.ResolvedResource resolved,
            ImageSourceCacheEntity boundImageSource,
            String explicitDigest,
            AuthContext ctx) {
        if (explicitDigest == null || explicitDigest.isBlank()) {
            throw new ValidationException(
                    "promote on a git ImageSource requires an explicit digest — git auto-promotes via the build pipeline");
        }

        var env = resolved.env();
        DeploymentEntity target = deploymentRepository
                .findFirstByResourceSlugAndImageRefOrderByCreatedAtDesc(slug, explicitDigest)
                .orElseThrow(() -> new NotFoundException(
                        "No historical deployment for resource " + slug + " with digest " + explicitDigest));

        var pin = new PinnedRelease();
        pin.setSourceCommit(target.getSourceRef());
        pin.setImageRef(explicitDigest);
        pin.setPinnedAt(Instant.now());
        pin.setPinnedFromDeploymentId(target.getId());

        applyResourcePin(slug, env.getSlug(), pin);
        recordDeploymentRow(slug, env.getId(), boundImageSource, explicitDigest, TriggerType.MANUAL);
        log.info("Promoted git resource {} to deployment {} (digest {})", slug, target.getId(), explicitDigest);

        auditLogService
                .audit(ctx, AuditAction.RESOURCE_UPDATED)
                .target(AuditTargetType.RESOURCE, slug)
                .inWorkspace(resolved.workspaceId())
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .change("promotedDigest", "", explicitDigest)
                .save();
    }

    /**
     * Pin {@code Resource.spec.pinnedRelease} to a historical deployment's snapshot. Works for
     * any ImageSource type — including {@code git} — because the pin lives on the Resource, not
     * on the ImageSource. The operator stops following the bound ImageSource's
     * {@code latestArtifact} until {@link #unpin} clears the pin.
     */
    @RetryOnConflict
    @Transactional
    public void rollback(String slug, UUID deploymentId, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        ImageSourceCacheEntity boundImageSource = loadBoundImageSource(slug);

        DeploymentEntity target = deploymentId != null
                ? deploymentRepository
                        .findByIdAndResourceSlug(deploymentId, slug)
                        .orElseThrow(() -> new NotFoundException("Deployment not found: " + deploymentId
                                + "; the row may have been pruned by retention policy"))
                : findPreviousSuccessfulDeployment(slug);

        if (target.getImageRef() == null || target.getImageRef().isBlank()) {
            throw new ValidationException("Deployment " + target.getId() + " has no imageRef; cannot roll back");
        }
        String targetImageRef = target.getImageRef();

        var pin = new PinnedRelease();
        pin.setSourceCommit(target.getSourceRef());
        pin.setImageRef(targetImageRef);
        pin.setPinnedAt(Instant.now());
        pin.setPinnedFromDeploymentId(target.getId());

        applyResourcePin(slug, env.getSlug(), pin);
        recordDeploymentRow(slug, env.getId(), boundImageSource, targetImageRef, TriggerType.ROLLBACK);
        log.info("Rolled back resource {} to deployment {} (digest {})", slug, target.getId(), targetImageRef);

        auditLogService
                .audit(ctx, AuditAction.RESOURCE_UPDATED)
                .target(AuditTargetType.RESOURCE, slug)
                .inWorkspace(workspaceId)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .change("rolledBackTo", "", target.getId().toString())
                .change("rolledBackDigest", "", targetImageRef)
                .save();
    }

    /**
     * Clear {@code Resource.spec.pinnedRelease}. The Resource immediately resumes following the
     * bound ImageSource's current {@code latestArtifact} (which may be newer than the pin if
     * builds continued while pinned). Mints a deployment audit row with
     * {@code triggered_by = UNPIN} so the timeline reflects the cause.
     */
    @RetryOnConflict
    @Transactional
    public void unpin(String slug, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.EDITOR);
        var env = resolved.env();
        UUID workspaceId = resolved.workspaceId();

        ImageSourceCacheEntity boundImageSource = loadBoundImageSource(slug);

        applyResourcePin(slug, env.getSlug(), null);
        // The ImageSource's current latestArtifact is what the Resource will catch up to. Record
        // it on the audit row so timelines show what the unpin landed on; null is acceptable when
        // the ImageSource hasn't produced an artifact yet.
        String catchUpRef = currentLatestArtifactRef(boundImageSource);
        recordDeploymentRow(slug, env.getId(), boundImageSource, catchUpRef, TriggerType.UNPIN);
        log.info("Unpinned resource {} (catching up to {})", slug, catchUpRef);

        auditLogService
                .audit(ctx, AuditAction.RESOURCE_UPDATED)
                .target(AuditTargetType.RESOURCE, slug)
                .inWorkspace(workspaceId)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .change("unpinned", "", catchUpRef != null ? catchUpRef : "")
                .save();
    }

    /**
     * Append a {@code deployment} audit row capturing the activation. Promote/rollback/unpin all
     * reuse an existing artifact (no build runs) — but the operations still represent a state
     * change worth tracking, and rollback's "previous deployment" lookup relies on a row existing
     * for each prior pin. Lifecycle starts at {@code ACTIVATING}; {@link ReleaseLifecycleEmitter}
     * on the operator advances it to {@code ACTIVE} once the rollout settles.
     */
    private void recordDeploymentRow(
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

    private static String currentLatestArtifactRef(ImageSourceCacheEntity bound) {
        if (bound == null || bound.getStatus() == null) {
            return null;
        }
        LatestArtifact artifact = bound.getStatus().getLatestArtifact();
        return artifact != null ? artifact.getImageRef() : null;
    }

    /**
     * Mutate the bound ImageSource's spec to pin {@code digest} and dispatch an
     * {@code ApplyResourceBundle} that updates only the ImageSource (no Resource change). Used
     * by {@link #promote} for {@code type=imageSource} (sets {@code pinnedDigest +
     * autoPromote=false}) and {@code type=image} (sets {@code spec.image.ref}). Git is rejected
     * upstream.
     */
    private void applyImageSourcePin(
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
     * Mutate {@code Resource.spec.pinnedRelease} (set or null) and dispatch an
     * {@code ApplyResourceBundle} that updates only the Resource (no ImageSource change).
     */
    private void applyResourcePin(String slug, String envSlug, PinnedRelease pin) {
        var existing = crdLookup.get(slug, envSlug);
        if (existing == null) {
            throw new NotFoundException("Resource CRD not found: " + slug);
        }
        existing.getSpec().setPinnedRelease(pin);
        crdClient.update(existing, null);
    }

    /**
     * The {@link ResourceCrdClient#update} contract takes a Resource + optional ImageSource; for
     * pin operations we pass through the existing Resource unchanged so the dispatcher has the
     * env-slug label needed for cluster routing.
     */
    private ResourceCrd buildResourcePassthrough(String slug, String envSlug) {
        var existing = crdLookup.get(slug, envSlug);
        if (existing == null) {
            throw new NotFoundException("Resource CRD not found: " + slug);
        }
        return existing;
    }
}

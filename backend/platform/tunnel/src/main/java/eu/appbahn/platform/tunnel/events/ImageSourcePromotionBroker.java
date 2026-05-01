package eu.appbahn.platform.tunnel.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.tunnel.ApplyResourceBundle;
import eu.appbahn.platform.resource.entity.ImageSourceCacheEntity;
import eu.appbahn.platform.resource.repository.ImageSourceCacheRepository;
import eu.appbahn.platform.tunnel.command.CommandEnqueueService;
import eu.appbahn.platform.tunnel.command.CommandTypes;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.imagesource.DownstreamReference;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourcePromotionSpec;
import eu.appbahn.shared.crd.imagesource.ImageSourceSpec;
import eu.appbahn.shared.crd.imagesource.ImageSourceStatus;
import eu.appbahn.shared.crd.imagesource.ImageSourceType;
import eu.appbahn.shared.crd.imagesource.ImageSourceUpstreamSpec;
import eu.appbahn.shared.crd.imagesource.LatestArtifact;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-cluster broker for {@code type: imageSource} promotion. When an upstream's
 * {@code latestArtifact} is observed via tunnel sync, this broker walks
 * {@link ImageSourceCacheRepository#findDownstreamPromotions} and, for each downstream that
 * lives on a different cluster and has {@code autoPromote=true}, enqueues an
 * {@link ApplyResourceBundle} command that updates the downstream's
 * {@code spec.imageSource.pinnedDigest} to the upstream's current digest.
 *
 * <p>Same-cluster promotion paths are handled entirely by the operator (which reads the upstream
 * CR directly) — the platform doesn't broker those. {@code autoPromote=false} downstream rows
 * are left untouched here; their pinnedDigest is set explicitly by the promote API.
 */
@Service
public class ImageSourcePromotionBroker {

    private static final Logger log = LoggerFactory.getLogger(ImageSourcePromotionBroker.class);

    private final ImageSourceCacheRepository imageSourceCacheRepository;
    private final EnvironmentRepository environmentRepository;
    private final CommandEnqueueService enqueue;
    private final ObjectMapper objectMapper;

    public ImageSourcePromotionBroker(
            ImageSourceCacheRepository imageSourceCacheRepository,
            EnvironmentRepository environmentRepository,
            CommandEnqueueService enqueue,
            ObjectMapper objectMapper) {
        this.imageSourceCacheRepository = imageSourceCacheRepository;
        this.environmentRepository = environmentRepository;
        this.enqueue = enqueue;
        this.objectMapper = objectMapper;
    }

    /**
     * Walk the downstream rows that point at this upstream and enqueue cross-cluster promotion
     * updates as needed. {@code upstreamCluster} is the cluster that emitted the sync event; the
     * downstream's {@code spec.imageSource.upstream.cluster} must match this exactly (or be
     * blank, which is treated as same-cluster) to be considered a match.
     */
    @Transactional
    public void brokerUpstreamUpdate(String upstreamCluster, String upstreamSlug) {
        var upstream = imageSourceCacheRepository.findBySlug(upstreamSlug).orElse(null);
        if (upstream == null) {
            return;
        }
        ImageSourceStatus status = upstream.getStatus();
        if (status == null) {
            return;
        }
        LatestArtifact artifact = status.getLatestArtifact();
        if (artifact == null
                || artifact.getImageRef() == null
                || artifact.getImageRef().isBlank()) {
            return;
        }
        String upstreamName = upstream.getSlug();
        String upstreamNamespace = upstream.getNamespace();
        if (upstreamNamespace == null) {
            return;
        }
        // Cluster matches happen on either the explicit cluster name or empty (same-cluster — the
        // operator handles that path). The same-cluster downstream rows are still walked so we
        // can spot misconfigurations, but we skip enqueuing for them.
        var downstreams =
                imageSourceCacheRepository.findDownstreamPromotions(upstreamName, upstreamNamespace, upstreamCluster);
        if (downstreams.isEmpty()) {
            // Also consider downstreams whose upstream.cluster is empty — same-cluster path,
            // handled by the operator alone.
            return;
        }
        for (ImageSourceCacheEntity downstream : downstreams) {
            handleDownstream(downstream, upstreamCluster, artifact);
        }
    }

    private void handleDownstream(
            ImageSourceCacheEntity downstream, String upstreamCluster, LatestArtifact upstreamArtifact) {
        ImageSourceSpec spec = downstream.getSpec();
        if (spec == null
                || spec.getType() != ImageSourceType.IMAGE_SOURCE
                || spec.getImageSource() == null
                || spec.getImageSource().getUpstream() == null) {
            return;
        }
        ImageSourcePromotionSpec promo = spec.getImageSource();
        EnvironmentEntity downstreamEnv = downstream.getEnvironmentId() == null
                ? null
                : environmentRepository.findById(downstream.getEnvironmentId()).orElse(null);
        if (downstreamEnv == null) {
            log.debug("Skipping downstream {} — no environment row", downstream.getSlug());
            return;
        }
        String downstreamCluster = downstreamEnv.getTargetCluster();
        if (downstreamCluster == null || downstreamCluster.equals(upstreamCluster)) {
            // Same-cluster downstream — operator handles it directly. Nothing for the broker.
            return;
        }
        if (!Boolean.TRUE.equals(promo.getAutoPromote())) {
            // Manual pin path — wait for explicit promote action to set pinnedDigest.
            return;
        }
        String desiredDigest = upstreamArtifact.getImageRef();
        if (desiredDigest.equals(promo.getPinnedDigest())) {
            // Already at the target digest — no work.
            return;
        }
        enqueueDigestUpdate(downstream, downstreamEnv, desiredDigest);
    }

    /**
     * Refresh the {@code appbahn.eu/downstream-references} annotation on an upstream
     * ImageSource so its operator can block hard-deletion when cross-cluster downstream
     * Resources still point at it. Same-cluster downstreams are observed by the upstream's
     * operator directly via the informer, so we only push the annotation when at least one
     * cross-cluster downstream exists — keeps the apply traffic minimal and avoids racing with
     * the upstream's GC (a refresh that arrives after the upstream has been hard-deleted would
     * SSA-create a ghost CR).
     *
     * <p>Called from {@code PushEventsHandler.handleImageSourceSyncBatch} after each downstream
     * row is persisted. Idempotent on the apply side; cheap to call.
     */
    @Transactional
    public void publishDownstreamReferences(String downstreamSlug) {
        var downstream = imageSourceCacheRepository.findBySlug(downstreamSlug).orElse(null);
        if (downstream == null
                || downstream.getSpec() == null
                || downstream.getSpec().getType() != ImageSourceType.IMAGE_SOURCE
                || downstream.getSpec().getImageSource() == null
                || downstream.getSpec().getImageSource().getUpstream() == null) {
            return;
        }
        ImageSourceUpstreamSpec upstreamCoord =
                downstream.getSpec().getImageSource().getUpstream();
        if (upstreamCoord.getName() == null || upstreamCoord.getNamespace() == null) {
            return;
        }
        ImageSourceCacheEntity upstream = imageSourceCacheRepository
                .findBySlug(upstreamCoord.getName())
                .filter(e -> Objects.equals(e.getNamespace(), upstreamCoord.getNamespace()))
                .orElse(null);
        if (upstream == null) {
            return;
        }
        EnvironmentEntity upstreamEnv = upstream.getEnvironmentId() == null
                ? null
                : environmentRepository.findById(upstream.getEnvironmentId()).orElse(null);
        if (upstreamEnv == null) {
            return;
        }
        // Compute the desired set of downstream references and filter to cross-cluster ones —
        // same-cluster downstreams are handled by the upstream's operator directly.
        var desired = computeDesiredReferences(upstream);
        var crossCluster = new ArrayList<DownstreamReference>();
        for (var ref : desired) {
            if (ref.getCluster() != null && !ref.getCluster().equals(upstreamEnv.getTargetCluster())) {
                crossCluster.add(ref);
            }
        }
        if (crossCluster.isEmpty()) {
            return;
        }
        var current = readCurrentReferences(upstream);
        if (referencesEqual(crossCluster, current)) {
            return;
        }
        enqueueAnnotationUpdate(upstream, upstreamEnv, crossCluster);
    }

    /**
     * If {@code slug} is a downstream {@code type: imageSource} row, return the upstream
     * coordinates it points at (so callers can re-publish after the downstream is deleted).
     * Returns null otherwise.
     */
    @Transactional(readOnly = true)
    public ImageSourceUpstreamSpec captureUpstreamForDownstream(String slug) {
        var entity = imageSourceCacheRepository.findBySlug(slug).orElse(null);
        if (entity == null
                || entity.getSpec() == null
                || entity.getSpec().getType() != ImageSourceType.IMAGE_SOURCE
                || entity.getSpec().getImageSource() == null
                || entity.getSpec().getImageSource().getUpstream() == null) {
            return null;
        }
        var upstream = entity.getSpec().getImageSource().getUpstream();
        var copy = new ImageSourceUpstreamSpec();
        copy.setCluster(upstream.getCluster());
        copy.setNamespace(upstream.getNamespace());
        copy.setName(upstream.getName());
        return copy;
    }

    /**
     * Republish the upstream's downstream-references annotation after a downstream row is gone.
     * Triggered from {@link PushEventsHandler#handleImageSourceDeletedBatch}. Only emits an
     * apply when at least one cross-cluster downstream remains — otherwise the annotation is
     * moot (same-cluster downstream removal is observed by the operator directly via the
     * informer; cross-cluster removal that drains the list is no-op for deletion safety because
     * the upstream's same-cluster check already covers same-cluster blockers, and emitting an
     * empty-array apply on an upstream whose K8s CR may already be drained risks SSA-creating a
     * new ghost CR in its place).
     */
    @Transactional
    public void refreshUpstreamReferencesAfterDownstreamRemoval(ImageSourceUpstreamSpec upstreamCoord) {
        if (upstreamCoord == null || upstreamCoord.getName() == null || upstreamCoord.getNamespace() == null) {
            return;
        }
        ImageSourceCacheEntity upstream = imageSourceCacheRepository
                .findBySlug(upstreamCoord.getName())
                .filter(e -> Objects.equals(e.getNamespace(), upstreamCoord.getNamespace()))
                .orElse(null);
        if (upstream == null) {
            return;
        }
        EnvironmentEntity upstreamEnv = upstream.getEnvironmentId() == null
                ? null
                : environmentRepository.findById(upstream.getEnvironmentId()).orElse(null);
        if (upstreamEnv == null) {
            return;
        }
        var desired = computeDesiredReferences(upstream);
        if (desired.isEmpty()) {
            return;
        }
        enqueueAnnotationUpdate(upstream, upstreamEnv, desired);
    }

    private List<DownstreamReference> computeDesiredReferences(ImageSourceCacheEntity upstream) {
        var rows = imageSourceCacheRepository.findAllDownstreamsByUpstream(upstream.getSlug(), upstream.getNamespace());
        var byEnvId = new HashMap<java.util.UUID, EnvironmentEntity>();
        for (var row : rows) {
            if (row.getEnvironmentId() != null && !byEnvId.containsKey(row.getEnvironmentId())) {
                environmentRepository.findById(row.getEnvironmentId()).ifPresent(e -> byEnvId.put(e.getId(), e));
            }
        }
        var refs = new ArrayList<DownstreamReference>();
        for (var row : rows) {
            EnvironmentEntity env = row.getEnvironmentId() == null ? null : byEnvId.get(row.getEnvironmentId());
            String cluster = env != null ? env.getTargetCluster() : null;
            var ref = new DownstreamReference();
            ref.setCluster(cluster);
            ref.setNamespace(row.getNamespace());
            ref.setName(row.getSlug());
            refs.add(ref);
        }
        refs.sort(Comparator.comparing((DownstreamReference r) -> r.getCluster() == null ? "" : r.getCluster())
                .thenComparing(r -> r.getNamespace() == null ? "" : r.getNamespace())
                .thenComparing(r -> r.getName() == null ? "" : r.getName()));
        return refs;
    }

    private List<DownstreamReference> readCurrentReferences(ImageSourceCacheEntity upstream) {
        if (upstream.getSpec() == null) return List.of();
        // Annotations are not on the cache spec — they're on metadata, not part of the spec.
        // The cache snapshot doesn't preserve metadata.annotations, so this returns empty until
        // the next operator round-trip refreshes status. The "no-op when equal" path is best
        // effort: a duplicate apply of the same annotation is harmless (SSA).
        return List.of();
    }

    private static boolean referencesEqual(List<DownstreamReference> a, List<DownstreamReference> b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            DownstreamReference x = a.get(i);
            DownstreamReference y = b.get(i);
            if (!Objects.equals(x.getCluster(), y.getCluster())
                    || !Objects.equals(x.getNamespace(), y.getNamespace())
                    || !Objects.equals(x.getName(), y.getName())) {
                return false;
            }
        }
        return true;
    }

    private void enqueueAnnotationUpdate(
            ImageSourceCacheEntity upstream, EnvironmentEntity upstreamEnv, List<DownstreamReference> refs) {
        ImageSourceCrd crd = new ImageSourceCrd();
        var meta = new ObjectMeta();
        meta.setName(upstream.getSlug());
        meta.setNamespace(upstream.getNamespace());
        meta.setLabels(Map.of(Labels.ENVIRONMENT_SLUG_KEY, upstreamEnv.getSlug()));
        try {
            String json = objectMapper.writeValueAsString(refs);
            meta.setAnnotations(Map.of(Labels.ANNOTATION_DOWNSTREAM_REFERENCES, json));
        } catch (Exception e) {
            log.warn("Failed to serialise downstream references for {}: {}", upstream.getSlug(), e.getMessage());
            return;
        }
        crd.setMetadata(meta);
        // Carry the spec through so SSA on the operator side keeps it; without spec the apply
        // path may overwrite live spec fields with nulls.
        ImageSourceSpec specCopy = objectMapper.convertValue(upstream.getSpec(), ImageSourceSpec.class);
        crd.setSpec(specCopy);

        var payload = new ApplyResourceBundle();
        payload.setNamespace(upstream.getNamespace());
        payload.setResource(null);
        payload.setImageSource(crd);

        enqueue.enqueue(upstreamEnv.getTargetCluster(), CommandTypes.APPLY_RESOURCE_BUNDLE, payload);
        log.info(
                "Brokered downstream-references annotation update: upstream {} on cluster {} → {} ref(s)",
                upstream.getSlug(),
                upstreamEnv.getTargetCluster(),
                refs.size());
    }

    private void enqueueDigestUpdate(ImageSourceCacheEntity downstream, EnvironmentEntity env, String desiredDigest) {
        ImageSourceCrd crd = new ImageSourceCrd();
        var meta = new ObjectMeta();
        meta.setName(downstream.getSlug());
        meta.setNamespace(downstream.getNamespace());
        meta.setLabels(Map.of(Labels.ENVIRONMENT_SLUG_KEY, env.getSlug()));
        crd.setMetadata(meta);
        // Deep-copy the existing spec so we don't mutate the cached row in-place; then update
        // pinnedDigest to the new digest.
        ImageSourceSpec specCopy = objectMapper.convertValue(downstream.getSpec(), ImageSourceSpec.class);
        if (specCopy.getImageSource() != null) {
            specCopy.getImageSource().setPinnedDigest(desiredDigest);
            // Cross-cluster auto-promote means the digest is the source of truth; leave
            // autoPromote=true so a future explicit unpin reverts cleanly.
        }
        crd.setSpec(specCopy);

        var payload = new ApplyResourceBundle();
        payload.setNamespace(downstream.getNamespace());
        // Resource-only edits use a null resource; here we want imageSource-only. Build a minimal
        // Resource shell so the operator's apply-bundle handler can SSA the ImageSource. Operator
        // is fine with a null resource (PR3b's PlatformCommandHandler accepts that path).
        payload.setResource(null);
        payload.setImageSource(crd);

        enqueue.enqueue(env.getTargetCluster(), CommandTypes.APPLY_RESOURCE_BUNDLE, payload);
        log.info(
                "Brokered cross-cluster promotion: downstream {} on cluster {} → pinnedDigest {}",
                downstream.getSlug(),
                env.getTargetCluster(),
                desiredDigest);
    }
}

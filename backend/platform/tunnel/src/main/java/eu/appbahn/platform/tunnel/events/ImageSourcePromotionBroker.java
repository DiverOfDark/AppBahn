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
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourcePromotionSpec;
import eu.appbahn.shared.crd.imagesource.ImageSourceSpec;
import eu.appbahn.shared.crd.imagesource.ImageSourceStatus;
import eu.appbahn.shared.crd.imagesource.ImageSourceType;
import eu.appbahn.shared.crd.imagesource.LatestArtifact;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.util.Map;
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

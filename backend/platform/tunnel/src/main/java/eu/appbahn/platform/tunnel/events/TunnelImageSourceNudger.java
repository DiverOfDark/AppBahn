package eu.appbahn.platform.tunnel.events;

import eu.appbahn.platform.api.tunnel.NudgeImageSource;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.resource.entity.ImageSourceCacheEntity;
import eu.appbahn.platform.resource.repository.ImageSourceCacheRepository;
import eu.appbahn.platform.resource.service.ImageSourceNudger;
import eu.appbahn.platform.tunnel.command.CommandEnqueueService;
import eu.appbahn.platform.tunnel.command.CommandTypes;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.shared.Labels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tunnel-backed {@link ImageSourceNudger}. Resolves the ImageSource's home cluster via the
 * cache row → environment → {@code environment.target_cluster} chain, then enqueues a
 * {@link NudgeImageSource} command on {@code pending_command} for that cluster's SSE drain
 * to pick up.
 */
@Service
public class TunnelImageSourceNudger implements ImageSourceNudger {

    private static final Logger log = LoggerFactory.getLogger(TunnelImageSourceNudger.class);

    private final CommandEnqueueService enqueue;
    private final ImageSourceCacheRepository imageSourceCache;
    private final EnvironmentRepository environmentRepository;

    public TunnelImageSourceNudger(
            CommandEnqueueService enqueue,
            ImageSourceCacheRepository imageSourceCache,
            EnvironmentRepository environmentRepository) {
        this.enqueue = enqueue;
        this.imageSourceCache = imageSourceCache;
        this.environmentRepository = environmentRepository;
    }

    @Override
    @Transactional
    public void nudge(String slug) {
        ImageSourceCacheEntity row = imageSourceCache
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("ImageSource not found: " + slug));
        EnvironmentEntity env = row.getEnvironmentId() == null
                ? null
                : environmentRepository.findById(row.getEnvironmentId()).orElse(null);
        String clusterName = env != null ? env.getTargetCluster() : Labels.DEFAULT_CLUSTER_NAME;

        var payload = new NudgeImageSource();
        payload.setNamespace(row.getNamespace());
        payload.setName(slug);
        enqueue.enqueue(clusterName, CommandTypes.NUDGE_IMAGE_SOURCE, payload);
        log.info("Enqueued NudgeImageSource cluster={} ns={} name={}", clusterName, row.getNamespace(), slug);
    }
}

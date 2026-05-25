package eu.appbahn.platform.tunnel.events;

import eu.appbahn.platform.api.tunnel.CancelBuild;
import eu.appbahn.platform.api.tunnel.RetryBuild;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.resource.entity.ImageSourceCacheEntity;
import eu.appbahn.platform.resource.repository.ImageSourceCacheRepository;
import eu.appbahn.platform.resource.service.DeploymentNudger;
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
 * Tunnel-backed {@link DeploymentNudger}. Resolves the home cluster from the ImageSource's cache
 * row (same chain as {@link TunnelImageSourceNudger}) and enqueues {@link CancelBuild} /
 * {@link RetryBuild} commands on {@code pending_command}.
 */
@Service
public class TunnelDeploymentNudger implements DeploymentNudger {

    private static final Logger log = LoggerFactory.getLogger(TunnelDeploymentNudger.class);

    private final CommandEnqueueService enqueue;
    private final ImageSourceCacheRepository imageSourceCache;
    private final EnvironmentRepository environmentRepository;

    public TunnelDeploymentNudger(
            CommandEnqueueService enqueue,
            ImageSourceCacheRepository imageSourceCache,
            EnvironmentRepository environmentRepository) {
        this.enqueue = enqueue;
        this.imageSourceCache = imageSourceCache;
        this.environmentRepository = environmentRepository;
    }

    @Override
    @Transactional
    public void cancelBuild(String namespace, String imageSourceName, String deploymentId) {
        String clusterName = resolveCluster(imageSourceName);
        var payload = new CancelBuild();
        payload.setNamespace(namespace);
        payload.setImageSourceName(imageSourceName);
        payload.setDeploymentId(deploymentId);
        enqueue.enqueue(clusterName, CommandTypes.CANCEL_BUILD, payload);
        log.info(
                "Enqueued CancelBuild cluster={} ns={} name={} deploymentId={}",
                clusterName,
                namespace,
                imageSourceName,
                deploymentId);
    }

    @Override
    @Transactional
    public void retryBuild(
            String namespace, String imageSourceName, String deploymentId, String sourceCommit, String imageRef) {
        String clusterName = resolveCluster(imageSourceName);
        var payload = new RetryBuild();
        payload.setNamespace(namespace);
        payload.setImageSourceName(imageSourceName);
        payload.setDeploymentId(deploymentId);
        payload.setSourceCommit(sourceCommit);
        payload.setImageRef(imageRef);
        enqueue.enqueue(clusterName, CommandTypes.RETRY_BUILD, payload);
        log.info(
                "Enqueued RetryBuild cluster={} ns={} name={} deploymentId={} commit={} imageRef={}",
                clusterName,
                namespace,
                imageSourceName,
                deploymentId,
                sourceCommit,
                imageRef);
    }

    private String resolveCluster(String imageSourceName) {
        ImageSourceCacheEntity row = imageSourceCache
                .findBySlug(imageSourceName)
                .orElseThrow(() -> new NotFoundException("ImageSource not found: " + imageSourceName));
        EnvironmentEntity env = row.getEnvironmentId() == null
                ? null
                : environmentRepository.findById(row.getEnvironmentId()).orElse(null);
        return env != null ? env.getTargetCluster() : Labels.DEFAULT_CLUSTER_NAME;
    }
}

package eu.appbahn.platform.tunnel.events;

import eu.appbahn.platform.tunnel.command.CommandEnqueueService;
import eu.appbahn.platform.tunnel.command.CommandTypes;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.platform.workspace.service.NamespaceCrdClient;
import eu.appbahn.tunnel.v1.ApplyNamespace;
import eu.appbahn.tunnel.v1.DeleteNamespace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform-side {@link NamespaceCrdClient}: namespace creates and deletes become
 * {@code pending_command} rows targeting the env's cluster. The operator picks them up
 * via {@code SubscribeCommands} and performs the actual K8s call.
 */
@Service
public class TunnelNamespaceCrdClient implements NamespaceCrdClient {

    private final CommandEnqueueService enqueue;
    private final EnvironmentRepository environmentRepository;

    public TunnelNamespaceCrdClient(CommandEnqueueService enqueue, EnvironmentRepository environmentRepository) {
        this.enqueue = enqueue;
        this.environmentRepository = environmentRepository;
    }

    @Override
    @Transactional
    public void apply(String environmentSlug, String namespace) {
        String clusterName = resolveCluster(environmentSlug);
        var cmd = ApplyNamespace.newBuilder()
                .setNamespace(namespace)
                .setEnvironmentSlug(environmentSlug)
                .build();
        enqueue.enqueue(clusterName, CommandTypes.APPLY_NAMESPACE, cmd);
    }

    @Override
    @Transactional
    public void delete(String namespace) {
        // Strip the prefix back to the env slug to look up the target cluster — the env row
        // is still present at this point because EnvironmentService.delete enqueues this
        // call before calling environmentRepository.delete().
        String envSlug = namespace.contains("-") ? namespace.substring(namespace.indexOf('-') + 1) : namespace;
        String clusterName = resolveCluster(envSlug);
        var cmd = DeleteNamespace.newBuilder().setNamespace(namespace).build();
        enqueue.enqueue(clusterName, CommandTypes.DELETE_NAMESPACE, cmd);
    }

    private String resolveCluster(String environmentSlug) {
        return environmentRepository
                .findBySlug(environmentSlug)
                .map(EnvironmentEntity::getTargetCluster)
                .orElseThrow(() -> new IllegalStateException(
                        "No environment found for slug '" + environmentSlug + "' when resolving target cluster"));
    }
}

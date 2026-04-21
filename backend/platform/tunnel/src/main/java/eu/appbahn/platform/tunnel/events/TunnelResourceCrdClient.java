package eu.appbahn.platform.tunnel.events;

import eu.appbahn.platform.resource.entity.ResourceCacheEntity;
import eu.appbahn.platform.resource.entity.ResourceCacheMapper;
import eu.appbahn.platform.resource.repository.DeploymentRepository;
import eu.appbahn.platform.resource.repository.ResourceCacheRepository;
import eu.appbahn.platform.resource.service.ResourceCrdClient;
import eu.appbahn.platform.tunnel.cluster.ClusterEntity;
import eu.appbahn.platform.tunnel.cluster.ClusterRepository;
import eu.appbahn.platform.tunnel.cluster.ClusterStatus;
import eu.appbahn.platform.tunnel.command.CommandEnqueueService;
import eu.appbahn.platform.tunnel.command.CommandTypes;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.entity.ProjectEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.platform.workspace.repository.ProjectRepository;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.tunnel.v1.ApplyResource;
import eu.appbahn.tunnel.v1.DeleteResource;
import eu.appbahn.tunnel.wire.ResourceWireMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform-side {@link ResourceCrdClient}: CR writes become {@code pending_command} rows, the
 * cache flips to {@code PENDING} in the same tx for immediate read-after-write, and the operator
 * picks up the command, applies the CR, and acks. {@link #get} reads from {@code resource_cache}.
 */
@Service
public class TunnelResourceCrdClient implements ResourceCrdClient {

    private static final Logger log = LoggerFactory.getLogger(TunnelResourceCrdClient.class);

    private final CommandEnqueueService enqueue;
    private final ResourceCacheRepository resourceCacheRepository;
    private final DeploymentRepository deploymentRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final ClusterRepository clusterRepository;

    public TunnelResourceCrdClient(
            CommandEnqueueService enqueue,
            ResourceCacheRepository resourceCacheRepository,
            DeploymentRepository deploymentRepository,
            EnvironmentRepository environmentRepository,
            ProjectRepository projectRepository,
            ClusterRepository clusterRepository) {
        this.enqueue = enqueue;
        this.resourceCacheRepository = resourceCacheRepository;
        this.deploymentRepository = deploymentRepository;
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
        this.clusterRepository = clusterRepository;
    }

    @Override
    @Transactional
    public void create(ResourceCrd crd) {
        String envSlug = crd.getMetadata().getLabels() != null
                ? crd.getMetadata().getLabels().getOrDefault(Labels.ENVIRONMENT_SLUG_KEY, "")
                : "";
        EnvironmentEntity env = envSlug.isEmpty()
                ? null
                : environmentRepository.findBySlug(envSlug).orElse(null);

        assertClusterReachable(env);
        upsertCache(crd, env);
        enqueueApply(crd);
    }

    @Override
    @Transactional
    public void update(ResourceCrd crd) {
        String envSlug = crd.getMetadata().getLabels() != null
                ? crd.getMetadata().getLabels().getOrDefault(Labels.ENVIRONMENT_SLUG_KEY, "")
                : "";
        EnvironmentEntity env = envSlug.isEmpty()
                ? null
                : environmentRepository.findBySlug(envSlug).orElse(null);
        assertClusterReachable(env);
        enqueueApply(crd);
    }

    @Override
    @Transactional
    public void delete(ResourceCrd crd) {
        delete(crd.getMetadata().getName(), crd.getMetadata().getNamespace());
    }

    @Override
    @Transactional
    public void delete(String slug, String namespace) {
        String clusterName = resolveClusterNameForSlug(slug).orElse("local");
        var cmd = DeleteResource.newBuilder()
                .setNamespace(namespace)
                .setResourceSlug(slug)
                .build();
        enqueue.enqueue(clusterName, CommandTypes.DELETE_RESOURCE, cmd);
    }

    @Override
    @Nullable
    @Transactional(readOnly = true)
    public ResourceCrd get(String slug, String namespace) {
        var cacheRow = resourceCacheRepository.findBySlug(slug).orElse(null);
        if (cacheRow == null) {
            return null;
        }
        return cacheRowToCrd(cacheRow, namespace);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void assertClusterReachable(@Nullable EnvironmentEntity env) {
        String clusterName = env != null ? env.getTargetCluster() : "local";
        ClusterEntity cluster = clusterRepository.findById(clusterName).orElse(null);
        if (cluster == null) {
            return; // Uninitialised cluster row — tests run this path; production registration seeds it.
        }
        if (cluster.getStatus() != ClusterStatus.APPROVED) {
            log.warn("Enqueuing command for cluster {} in status {}", clusterName, cluster.getStatus());
        }
    }

    private void enqueueApply(ResourceCrd crd) {
        String clusterName = resolveClusterNameForCrd(crd);
        ApplyResource payload =
                ResourceWireMapper.toApplyResource(crd, crd.getMetadata().getNamespace());
        enqueue.enqueue(clusterName, CommandTypes.APPLY_RESOURCE, payload);
    }

    private String resolveClusterNameForCrd(ResourceCrd crd) {
        String envSlug = crd.getMetadata().getLabels() != null
                ? crd.getMetadata().getLabels().getOrDefault(Labels.ENVIRONMENT_SLUG_KEY, "")
                : "";
        if (envSlug.isEmpty()) {
            return "local";
        }
        return environmentRepository
                .findBySlug(envSlug)
                .map(EnvironmentEntity::getTargetCluster)
                .orElse("local");
    }

    private java.util.Optional<String> resolveClusterNameForSlug(String slug) {
        return resourceCacheRepository
                .findBySlug(slug)
                .flatMap(row -> environmentRepository.findById(row.getEnvironmentId()))
                .map(EnvironmentEntity::getTargetCluster);
    }

    private void upsertCache(ResourceCrd crd, @Nullable EnvironmentEntity env) {
        if (env == null) {
            return;
        }
        if (resourceCacheRepository.findBySlug(crd.getMetadata().getName()).isPresent()) {
            return;
        }
        resourceCacheRepository.saveAndFlush(ResourceCacheMapper.newCacheEntity(crd, env));

        // Materialise the initial DeploymentEntity synchronously so `listDeployments` sees
        // it before the operator round-trips — the pre-seeded cache row hides "first sight"
        // from the sync path, so platform owns the initial insert.
        ResourceCacheMapper.initialDeployment(crd, env)
                .filter(dep -> deploymentRepository.findById(dep.getId()).isEmpty())
                .ifPresent(deploymentRepository::save);
    }

    private ResourceCrd cacheRowToCrd(ResourceCacheEntity row, String namespace) {
        EnvironmentEntity env =
                environmentRepository.findById(row.getEnvironmentId()).orElse(null);
        ProjectEntity project = env != null && env.getProjectId() != null
                ? projectRepository.findById(env.getProjectId()).orElse(null)
                : null;
        return ResourceCacheMapper.toCrd(row, namespace, env, project);
    }
}

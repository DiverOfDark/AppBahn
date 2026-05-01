package eu.appbahn.platform.resource.entity;

import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.entity.ProjectEntity;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceSpec;
import eu.appbahn.shared.crd.ResourceStatusDetail;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.time.Instant;
import java.util.Map;

/**
 * Field-level translation between {@link ResourceCacheEntity} (platform-owned cache row)
 * and {@link ResourceCrd} (Kubernetes-side POJO). Lives next to the entity so callers
 * inside {@code platform:tunnel} (CRD client) and elsewhere see one canonical mapping.
 */
public final class ResourceCacheMapper {

    private ResourceCacheMapper() {}

    /**
     * Hydrate a CRD POJO from a cache row. Resolves env/project/workspace IDs by walking
     * the entity graph; the environment-slug label is set so downstream wire-mapping can
     * pick it up.
     */
    public static ResourceCrd toCrd(
            ResourceCacheEntity row, String namespace, EnvironmentEntity env, ProjectEntity project) {
        var crd = new ResourceCrd();
        var meta = new ObjectMeta();
        meta.setName(row.getSlug());
        meta.setNamespace(namespace);
        crd.setMetadata(meta);

        var spec = new ResourceSpec();
        spec.setName(row.getName());
        spec.setType(row.getType());
        spec.setConfig(row.getConfig());
        spec.setLinks(row.getLinks());

        if (env != null) {
            spec.setEnvironmentId(env.getId().toString());
            if (env.getProjectId() != null) {
                spec.setProjectId(env.getProjectId().toString());
            }
            if (project != null && project.getWorkspaceId() != null) {
                spec.setWorkspaceId(project.getWorkspaceId().toString());
            }
            meta.setLabels(Map.of(Labels.ENVIRONMENT_SLUG_KEY, env.getSlug()));
        }
        // stopped isn't its own column — infer from phase so ResourceLifecycleService.start/stop
        // can short-circuit on already-stopped resources.
        if (row.getStatus() == ResourcePhase.STOPPED) {
            spec.setStopped(true);
        }
        spec.setPinnedRelease(row.getPinnedRelease());
        spec.setCommandOverride(row.getCommandOverride());
        crd.setSpec(spec);
        crd.setStatus(row.getStatusDetail() != null ? row.getStatusDetail() : new ResourceStatusDetail());
        return crd;
    }

    /** Build a fresh PENDING cache row from a CRD that the platform just enqueued. */
    public static ResourceCacheEntity newCacheEntity(ResourceCrd crd, EnvironmentEntity env) {
        ResourceSpec spec = crd.getSpec();
        Instant now = Instant.now();
        var entity = new ResourceCacheEntity();
        entity.setSlug(crd.getMetadata().getName());
        entity.setEnvironmentId(env.getId());
        entity.setName(
                spec.getName() != null ? spec.getName() : crd.getMetadata().getName());
        entity.setType(spec.getType() != null ? spec.getType() : Labels.RESOURCE_TYPE_DEPLOYMENT);
        entity.setConfig(spec.getConfig());
        entity.setLinks(spec.getLinks());
        entity.setStatus(ResourcePhase.PENDING);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setLastSyncedAt(null);
        return entity;
    }
}

package eu.appbahn.shared.tunnel;

import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceSpec;
import eu.appbahn.tunnel.v1.ApplyResource;
import eu.appbahn.tunnel.v1.Resource;
import eu.appbahn.tunnel.v1.ResourceSyncItem;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.util.Collections;
import java.util.Map;

/**
 * Bidirectional mapper between the {@link ResourceCrd} POJO (Kubernetes-side) and the
 * tunnel proto wrappers ({@link Resource}, {@link ApplyResource}, {@link ResourceSyncItem}).
 *
 * <p>Owns the field-level translation in one place so {@code OperatorEventPublisher},
 * {@code PlatformCommandHandler}, {@code TunnelResourceCrdClient} and
 * {@code ResourceAdmissionController} all share the same shape.
 */
public final class ResourceWireMapper {

    private ResourceWireMapper() {}

    // -------------------------------------------------------------------------
    // CRD → wire
    // -------------------------------------------------------------------------

    /** Build the OpenAPI-shaped {@link Resource} message from a CRD. {@code envSlug} comes from
     * the caller because the CRD may have it on a label or not at all. */
    public static Resource toResource(ResourceCrd crd, String envSlug) {
        String slug = crd.getMetadata() != null && crd.getMetadata().getName() != null
                ? crd.getMetadata().getName()
                : "";
        ResourceSpec spec = crd.getSpec();
        String name = spec != null && spec.getName() != null ? spec.getName() : slug;
        String type = spec != null && spec.getType() != null ? spec.getType() : Labels.RESOURCE_TYPE_DEPLOYMENT;

        var builder = Resource.newBuilder()
                .setSlug(slug)
                .setName(name)
                .setType(type)
                .setEnvironmentSlug(envSlug != null ? envSlug : "");

        if (spec != null && spec.getConfig() != null) {
            builder.setConfig(ProtoCrdMapper.toProto(spec.getConfig()));
        }
        if (spec != null && spec.getLinks() != null && !spec.getLinks().isEmpty()) {
            builder.addAllLinks(ProtoCrdMapper.toProto(spec.getLinks()));
        }

        var status = crd.getStatus();
        ResourcePhase phase = status != null && status.getPhase() != null ? status.getPhase() : ResourcePhase.PENDING;
        builder.setStatus(phase.name());
        if (status != null) {
            builder.setStatusDetail(ProtoCrdMapper.toProto(status));
        }

        if (crd.getMetadata() != null && crd.getMetadata().getCreationTimestamp() != null) {
            // K8s already emits ISO-8601; pass it through verbatim.
            builder.setCreatedAt(crd.getMetadata().getCreationTimestamp());
        }
        return builder.build();
    }

    /** Convenience: pull envSlug from the CRD's {@code appbahn.eu/environment-slug} label. */
    public static Resource toResource(ResourceCrd crd) {
        return toResource(crd, environmentSlugLabel(crd));
    }

    /** {@link ResourceSyncItem} = Resource + K8s metadata (generation, resourceVersion). */
    public static ResourceSyncItem toSyncItem(ResourceCrd crd) {
        var item = ResourceSyncItem.newBuilder().setResource(toResource(crd));
        if (crd.getMetadata() != null) {
            if (crd.getMetadata().getGeneration() != null) {
                item.setGeneration(crd.getMetadata().getGeneration());
            }
            if (crd.getMetadata().getResourceVersion() != null) {
                item.setResourceVersion(crd.getMetadata().getResourceVersion());
            }
        }
        return item.build();
    }

    /** {@link ApplyResource} = Resource + spec-internal IDs that don't appear in the user-facing
     * Resource model (they're stored in resource_cache and surfaced as labels on the cluster). */
    public static ApplyResource toApplyResource(ResourceCrd crd, String namespace) {
        ResourceSpec spec = crd.getSpec() != null ? crd.getSpec() : new ResourceSpec();
        var builder = ApplyResource.newBuilder()
                .setNamespace(namespace != null ? namespace : "")
                .setResource(toResource(crd));
        if (spec.getEnvironmentId() != null) builder.setEnvironmentId(spec.getEnvironmentId());
        if (spec.getProjectId() != null) builder.setProjectId(spec.getProjectId());
        if (spec.getWorkspaceId() != null) builder.setWorkspaceId(spec.getWorkspaceId());
        if (spec.getDeploymentRevision() != null) builder.setDeploymentRevision(spec.getDeploymentRevision());
        if (spec.getStopped() != null) builder.setStopped(spec.getStopped());
        return builder.build();
    }

    // -------------------------------------------------------------------------
    // Wire → CRD
    // -------------------------------------------------------------------------

    /** Build a {@link ResourceCrd} from an {@link ApplyResource}. {@code fallbackEnvSlug} is
     * used when the wire payload didn't carry one — typically the operator's admission cache
     * resolves namespace → env-slug. */
    public static ResourceCrd toCrd(ApplyResource apply, String fallbackEnvSlug) {
        var resource = apply.getResource();
        var crd = new ResourceCrd();
        var meta = new ObjectMeta();
        meta.setName(resource.getSlug());
        meta.setNamespace(apply.getNamespace());

        String envSlug = !resource.getEnvironmentSlug().isBlank()
                ? resource.getEnvironmentSlug()
                : (fallbackEnvSlug != null ? fallbackEnvSlug : "");
        if (!envSlug.isBlank()) {
            meta.setLabels(Map.of(Labels.ENVIRONMENT_SLUG_KEY, envSlug));
        }
        crd.setMetadata(meta);

        var spec = new ResourceSpec();
        spec.setName(resource.getName().isEmpty() ? resource.getSlug() : resource.getName());
        spec.setType(resource.getType().isEmpty() ? Labels.RESOURCE_TYPE_DEPLOYMENT : resource.getType());
        spec.setConfig(resource.hasConfig() ? ProtoCrdMapper.fromProto(resource.getConfig()) : new ResourceConfig());
        spec.setLinks(ProtoCrdMapper.linksFromProto(resource.getLinksList()));
        if (!apply.getEnvironmentId().isEmpty()) spec.setEnvironmentId(apply.getEnvironmentId());
        if (!apply.getProjectId().isEmpty()) spec.setProjectId(apply.getProjectId());
        if (!apply.getWorkspaceId().isEmpty()) spec.setWorkspaceId(apply.getWorkspaceId());
        if (!apply.getDeploymentRevision().isEmpty()) spec.setDeploymentRevision(apply.getDeploymentRevision());
        if (apply.hasStopped()) spec.setStopped(apply.getStopped());
        crd.setSpec(spec);
        return crd;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public static String environmentSlugLabel(ResourceCrd crd) {
        if (crd.getMetadata() == null) return "";
        Map<String, String> labels =
                crd.getMetadata().getLabels() != null ? crd.getMetadata().getLabels() : Collections.emptyMap();
        return labels.getOrDefault(Labels.ENVIRONMENT_SLUG_KEY, "");
    }
}

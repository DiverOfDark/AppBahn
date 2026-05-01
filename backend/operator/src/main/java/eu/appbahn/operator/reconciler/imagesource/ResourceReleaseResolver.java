package eu.appbahn.operator.reconciler.imagesource;

import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.crd.imagesource.LatestArtifact;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.Optional;

/**
 * Pure helpers that resolve a Resource → ImageSource binding. The binding is by convention:
 * the sibling ImageSource has the same {@code metadata.name} in the same namespace. Same-namespace
 * lookup only; cross-namespace promotion is a different code path (a downstream
 * {@code type: imageSource} ImageSource).
 */
public final class ResourceReleaseResolver {

    private ResourceReleaseResolver() {}

    /** Sibling ImageSource name = the Resource's own {@code metadata.name}. */
    public static String boundImageSourceName(ResourceCrd primary) {
        if (primary == null || primary.getMetadata() == null) {
            return null;
        }
        String name = primary.getMetadata().getName();
        return (name == null || name.isBlank()) ? null : name;
    }

    /**
     * Look up the bound ImageSource via the JOSDK Context's K8s client and return its
     * {@code status.latestArtifact} if present. Returns empty when the ImageSource doesn't
     * exist yet or no artifact has been built.
     */
    public static Optional<LatestArtifact> resolveLatestArtifact(ResourceCrd primary, Context<ResourceCrd> context) {
        return resolveImageSource(primary, context)
                .flatMap(is -> Optional.ofNullable(is.getStatus()).map(s -> s.getLatestArtifact()));
    }

    /** Same as {@link #resolveLatestArtifact} but unwraps to {@code imageRef} for convenience. */
    public static Optional<String> resolveImageRef(ResourceCrd primary, Context<ResourceCrd> context) {
        return resolveLatestArtifact(primary, context)
                .map(LatestArtifact::getImageRef)
                .filter(ref -> ref != null && !ref.isBlank());
    }

    public static Optional<ImageSourceCrd> resolveImageSource(ResourceCrd primary, Context<ResourceCrd> context) {
        String name = boundImageSourceName(primary);
        if (name == null) {
            return Optional.empty();
        }
        String namespace = primary.getMetadata().getNamespace();
        if (namespace == null) {
            return Optional.empty();
        }
        KubernetesClient client = context.getClient();
        var fetched = client.resources(ImageSourceCrd.class)
                .inNamespace(namespace)
                .withName(name)
                .get();
        return Optional.ofNullable(fetched);
    }
}

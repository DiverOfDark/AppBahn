package eu.appbahn.operator.reconciler.imagesource;

import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.crd.imagesource.LatestArtifact;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.Optional;

/**
 * Pure helpers that resolve a Resource → ImageSource binding back to the K8s ImageSource CR and
 * its {@code status.latestArtifact}. Same-namespace lookup only; cross-namespace promotion is
 * a different code path (a downstream {@code type: imageSource} ImageSource).
 */
public final class ResourceReleaseResolver {

    private ResourceReleaseResolver() {}

    /** True when {@code spec.release.fromImageSource.name} is set on the Resource. */
    public static boolean usesReleasePath(ResourceCrd primary) {
        if (primary.getSpec() == null || primary.getSpec().getRelease() == null) {
            return false;
        }
        var fromImageSource = primary.getSpec().getRelease().getFromImageSource();
        return fromImageSource != null
                && fromImageSource.getName() != null
                && !fromImageSource.getName().isBlank();
    }

    /** Sibling ImageSource name from {@code spec.release.fromImageSource.name}, or null. */
    public static String boundImageSourceName(ResourceCrd primary) {
        if (!usesReleasePath(primary)) {
            return null;
        }
        return primary.getSpec().getRelease().getFromImageSource().getName();
    }

    /**
     * Look up the bound ImageSource via the JOSDK Context's K8s client and return its
     * {@code status.latestArtifact} if present. Returns empty when the binding isn't set, the
     * ImageSource doesn't exist, or no artifact has been built yet.
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
        String namespace = primary.getMetadata() != null ? primary.getMetadata().getNamespace() : null;
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

package eu.appbahn.operator.reconciler.imagesource;

import eu.appbahn.shared.crd.PinnedRelease;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.crd.imagesource.LatestArtifact;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.Optional;

/**
 * Pure helpers that resolve a Resource → release artifact. The artifact is decided at the
 * Resource layer, independent of ImageSource type:
 *
 * <ol>
 *   <li>{@code spec.pinnedRelease} is non-null → use the pinned snapshot (fast rollback).</li>
 *   <li>Else → use the sibling ImageSource's {@code status.latestArtifact}. Binding is by
 *       same {@code metadata.name} in the same namespace.</li>
 *   <li>Neither → empty (Resource stays in {@code Pending} with reason {@code NoArtifact}).</li>
 * </ol>
 *
 * <p>This split lets the ImageSource keep doing its job (track HEAD, build commits) while the
 * Resource decides which historical artifact to actually run — Vercel/Railway/Heroku semantics.
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
     * Resolve the artifact the Resource should run: pin first, then ImageSource latestArtifact.
     * Returns empty when neither is available — the Resource waits in {@code Pending}.
     */
    public static Optional<LatestArtifact> resolveActiveArtifact(ResourceCrd primary, Context<ResourceCrd> context) {
        Optional<LatestArtifact> pinned = artifactFromPin(primary);
        if (pinned.isPresent()) {
            return pinned;
        }
        return resolveLatestArtifact(primary, context);
    }

    /** Same as {@link #resolveActiveArtifact} but unwraps to {@code imageRef} for convenience. */
    public static Optional<String> resolveImageRef(ResourceCrd primary, Context<ResourceCrd> context) {
        return resolveActiveArtifact(primary, context)
                .map(LatestArtifact::getImageRef)
                .filter(ref -> ref != null && !ref.isBlank());
    }

    /**
     * Sibling ImageSource's {@code status.latestArtifact}, ignoring any Resource-side pin. Used by
     * the build/audit layer that needs to know "what is the source-of-truth current build" rather
     * than "what is currently rolled out".
     */
    public static Optional<LatestArtifact> resolveLatestArtifact(ResourceCrd primary, Context<ResourceCrd> context) {
        return resolveImageSource(primary, context)
                .flatMap(is -> Optional.ofNullable(is.getStatus()).map(s -> s.getLatestArtifact()));
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

    /**
     * Project a {@link PinnedRelease} into a {@link LatestArtifact} so callers can treat both
     * sources uniformly. Returns empty when no pin is set or when the pin lacks an imageRef.
     */
    private static Optional<LatestArtifact> artifactFromPin(ResourceCrd primary) {
        if (primary == null || primary.getSpec() == null) {
            return Optional.empty();
        }
        PinnedRelease pin = primary.getSpec().getPinnedRelease();
        if (pin == null || pin.getImageRef() == null || pin.getImageRef().isBlank()) {
            return Optional.empty();
        }
        var artifact = new LatestArtifact();
        artifact.setSourceCommit(pin.getSourceCommit());
        artifact.setImageRef(pin.getImageRef());
        artifact.setRunCommand(pin.getRunCommand());
        artifact.setBuiltAt(pin.getPinnedAt());
        return Optional.of(artifact);
    }
}

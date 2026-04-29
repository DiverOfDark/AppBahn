package eu.appbahn.operator.reconciler.imagesource.buildjob;

import eu.appbahn.shared.crd.imagesource.BuildMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Operator config for build infrastructure: per-mode builder image refs (helm-driven) and the
 * push-target registry. Helm wires env vars
 * {@code OPERATOR_BUILD_BUILDERS_<MODE>_IMAGE / _ENTRYPOINT_<n>} into Spring's relaxed
 * binding.
 */
@ConfigurationProperties(prefix = "operator.build")
public record BuilderConfig(
        @DefaultValue Map<String, BuilderImage> builders,

        @DefaultValue("appbahn-registry.appbahn-system.svc:5000")
        String registry,

        @DefaultValue("true") boolean registryInsecure,
        String registryCredentialsSecret,
        @DefaultValue("git-clone:v1") String gitCloneImage,
        @DefaultValue("3600") int activeDeadlineSeconds,
        @DefaultValue("60") int ttlSecondsAfterFinished) {

    /** Looks up a builder image by mode, throwing when the mode isn't registered. */
    public BuilderImage forMode(BuildMode mode) {
        BuilderImage image = builders.get(mode.name().toLowerCase());
        if (image == null || image.image() == null || image.image().isBlank()) {
            throw new IllegalStateException("no builder image configured for mode " + mode.name()
                    + " (operator.build.builders." + mode.name().toLowerCase() + ".image)");
        }
        return image;
    }

    public record BuilderImage(String image, @DefaultValue List<String> entrypoint) {

        /** Convenience: never returns null — falls back to the empty list. */
        public List<String> entrypointOrEmpty() {
            return entrypoint == null ? Collections.emptyList() : entrypoint;
        }

        /**
         * Build a {@link BuilderImage} from a comma-separated entrypoint string. Helm sets a
         * single env var per builder (e.g. {@code OPERATOR_BUILD_BUILDERS_PEELBOX_ENTRYPOINT})
         * with comma-separated tokens — easier for Spring relaxed binding than per-index env
         * vars.
         */
        public static BuilderImage of(String image, String entrypointCsv) {
            if (entrypointCsv == null || entrypointCsv.isBlank()) {
                return new BuilderImage(image, Collections.emptyList());
            }
            List<String> tokens = new java.util.ArrayList<>();
            for (String t : entrypointCsv.split(",", -1)) {
                tokens.add(t);
            }
            return new BuilderImage(image, tokens);
        }
    }
}

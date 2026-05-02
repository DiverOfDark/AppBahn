package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Discriminator for {@link ImageSourceSpec}. Each value selects which sibling sub-block on the
 * spec is meaningful: {@code git} → {@code spec.git}, {@code image} → {@code spec.image},
 * {@code imageSource} → {@code spec.imageSource} (promotion chain — follows another
 * ImageSource's {@code latestArtifact}). The {@code registry} (tag watcher) variant is
 * deferred — see #109.
 */
public enum ImageSourceType {
    @JsonProperty("Git")
    GIT,

    @JsonProperty("Image")
    IMAGE,

    @JsonProperty("ImageSource")
    IMAGE_SOURCE
}

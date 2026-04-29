package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Discriminator for {@link ImageSourceSpec}. Each value selects which sibling sub-block
 * on the spec is meaningful: {@code git} → {@code spec.git}, {@code image} → {@code spec.image}.
 * Other variants (imageSource promotion chain, registry watcher) land in later PRs.
 */
public enum ImageSourceType {
    @JsonProperty("git")
    GIT,
    @JsonProperty("image")
    IMAGE
}

package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Build configuration for {@code type: git} ImageSources. {@code mode} selects which
 * sibling sub-block carries options. {@code peelbox}, {@code nixpacks}, {@code railpack}
 * have no options (auto-detect) — their option blocks stay null. Build execution itself
 * lands in a later PR; this PR only declares the schema.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageSourceBuildSpec {
    private BuildMode mode;
    private DockerfileBuildOptions dockerfile;
    private BuildpackBuildOptions buildpack;
}

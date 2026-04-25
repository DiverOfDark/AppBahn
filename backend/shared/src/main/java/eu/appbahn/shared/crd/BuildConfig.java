package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sealed marker for the polymorphic build config. Same shape as {@link Source} — Jackson
 * polymorphism only, no shared fields. The spec-side flattening lives in
 * {@code OpenApiConfig#flattenPolymorphicSubtypesCustomizer}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PeelboxBuildConfig.class, name = "peelbox"),
    @JsonSubTypes.Type(value = BuildpackBuildConfig.class, name = "buildpack"),
    @JsonSubTypes.Type(value = RailpackBuildConfig.class, name = "railpack"),
    @JsonSubTypes.Type(value = DockerfileBuildConfig.class, name = "dockerfile")
})
@Schema(
        oneOf = {
            PeelboxBuildConfig.class,
            BuildpackBuildConfig.class,
            RailpackBuildConfig.class,
            DockerfileBuildConfig.class
        },
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "peelbox", schema = PeelboxBuildConfig.class),
            @DiscriminatorMapping(value = "buildpack", schema = BuildpackBuildConfig.class),
            @DiscriminatorMapping(value = "railpack", schema = RailpackBuildConfig.class),
            @DiscriminatorMapping(value = "dockerfile", schema = DockerfileBuildConfig.class)
        })
public sealed interface BuildConfig
        permits PeelboxBuildConfig, BuildpackBuildConfig, RailpackBuildConfig, DockerfileBuildConfig {}

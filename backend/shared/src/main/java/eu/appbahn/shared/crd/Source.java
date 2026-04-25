package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sealed marker for the polymorphic resource source. The interface carries Jackson
 * polymorphism config but has no methods or fields — every subtype declares its own
 * {@code type} / {@code pollInterval} / {@code webhookEnabled} directly. Springdoc emits each
 * subtype as {@code allOf: [$ref(SourceConfig), {full inline}]}; the
 * {@code flattenPolymorphicSubtypesCustomizer} in {@code OpenApiConfig} rewrites the spec into
 * a pure {@code oneOf} wrapper + flat standalone subtypes before the spec is committed.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = DockerSource.class, name = "docker"),
    @JsonSubTypes.Type(value = GitSource.class, name = "git"),
    @JsonSubTypes.Type(value = PromotionSource.class, name = "promotion")
})
@Schema(
        name = "SourceConfig",
        oneOf = {DockerSource.class, GitSource.class, PromotionSource.class},
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "docker", schema = DockerSource.class),
            @DiscriminatorMapping(value = "git", schema = GitSource.class),
            @DiscriminatorMapping(value = "promotion", schema = PromotionSource.class)
        })
public sealed interface Source permits DockerSource, GitSource, PromotionSource {}

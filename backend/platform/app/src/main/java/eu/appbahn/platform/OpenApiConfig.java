package eu.appbahn.platform;

import eu.appbahn.platform.api.ErrorResponse;
import io.fabric8.kubernetes.api.model.Quantity;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes two OpenAPI groups:
 * <ul>
 *   <li>{@code public} — user-facing REST at {@code /api/v1/**}, served at
 *       {@code /api/v1/openapi.yaml}.</li>
 *   <li>{@code tunnel} — operator ↔ platform tunnel at {@code /api/tunnel/v1/**},
 *       served at {@code /api/tunnel/v1/openapi.yaml}.</li>
 * </ul>
 * Each is dumped into a separate committed spec file; downstream clients treat them as
 * independent contracts.
 */
@Configuration
class OpenApiConfig {

    static {
        // fabric8's Quantity serialises as a bare string on the wire ("100m", "512Mi"); register
        // once, globally, so every controller method that accepts or returns a Quantity emits
        // `type: string` in the OpenAPI spec instead of the {amount, format} structural view
        // springdoc would otherwise derive.
        ModelConverters.getInstance().addConverter(new QuantityAsStringConverter());
    }

    @Bean
    OpenAPI appbahnOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AppBahn Platform API")
                        .description("Enterprise PaaS on your own Kubernetes infrastructure.")
                        .version("1.0.0")
                        .license(new License().name("ELv2").url("https://www.elastic.co/licensing/elastic-license")))
                .servers(List.of(new Server().url("/api/v1")));
    }

    @Bean
    GroupedOpenApi publicApiGroup() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/v1/**")
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new Info()
                                    .title("AppBahn Platform API")
                                    .description("Enterprise PaaS on your own Kubernetes infrastructure.")
                                    .version("1.0.0")
                                    .license(new License()
                                            .name("ELv2")
                                            .url("https://www.elastic.co/licensing/elastic-license")))
                            .servers(List.of(new Server().url("/api/v1")));
                    // Clients (web, CLI) authenticate with a short-lived bearer token.
                    Components components =
                            openApi.getComponents() == null ? new Components() : openApi.getComponents();
                    components.addSecuritySchemes(
                            "bearerAuth",
                            new SecurityScheme()
                                    .type(SecurityScheme.Type.HTTP)
                                    .scheme("bearer")
                                    .bearerFormat("JWT"));
                    openApi.components(components);
                    openApi.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                })
                .addOpenApiCustomizer(stripPathPrefixCustomizer("/api/v1/"))
                .addOpenApiCustomizer(errorResponseSchemaCustomizer())
                .addOpenApiCustomizer(referencePublicPolymorphicFieldsCustomizer())
                .addOpenApiCustomizer(flattenPolymorphicSubtypesCustomizer("SourceConfig", "BuildConfig"))
                .addOpenApiCustomizer(stripNullableCustomizer())
                .build();
    }

    @Bean
    GroupedOpenApi tunnelApiGroup() {
        return GroupedOpenApi.builder()
                .group("tunnel")
                .pathsToMatch("/api/tunnel/**")
                .addOpenApiCustomizer(openApi -> openApi.info(new Info()
                                .title("AppBahn Operator Tunnel API")
                                .description(
                                        "Private operator ↔ platform transport. Unary REST + a single SSE stream (commands).")
                                .version("1.0.0"))
                        .servers(List.of(new Server().url("/api/tunnel/v1"))))
                .addOpenApiCustomizer(stripPathPrefixCustomizer("/api/tunnel/v1/"))
                .addOpenApiCustomizer(sseFrameSchemasCustomizer())
                .addOpenApiCustomizer(referenceOperatorEventCustomizer())
                .addOpenApiCustomizer(flattenPolymorphicSubtypesCustomizer("SourceConfig", "BuildConfig"))
                .addOpenApiCustomizer(stripNullableCustomizer())
                .build();
    }

    /**
     * Springdoc emits {@code nullable: true} on every field typed with {@code @Nullable} (Spring,
     * JetBrains) or {@code Optional}. Our clients (openapi-typescript, openapi-generator go)
     * translate that to {@code string | null} — useless noise, because presence is already
     * signalled by the {@code required} array, and all absent nullable fields arrive as
     * {@code undefined}/zero-value rather than literal null. Strip it across the board.
     */
    private OpenApiCustomizer stripNullableCustomizer() {
        return openApi -> stripNullableRecursively(openApi);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void stripNullableRecursively(Object node) {
        if (node instanceof io.swagger.v3.oas.models.media.Schema<?> schema) {
            if (Boolean.TRUE.equals(schema.getNullable())) {
                schema.setNullable(null);
            }
            // Springdoc emits `default: ""` on enum schemas, which makes openapi-generator's
            // Java template emit broken code: `private FooEnum f = ;`. Strip the empty-string
            // default so the generated field has no initialiser. (Spurious `default: null` from
            // springdoc is stripped at YAML emit time — Schema.setDefault(null) doesn't survive
            // Jackson serialisation since the field has no @JsonInclude(NON_NULL).)
            if ("".equals(schema.getDefault()) && schema.getEnum() != null) {
                schema.setDefault(null);
            }
            Map<String, ?> props = schema.getProperties();
            if (props != null) {
                for (Object v : props.values()) stripNullableRecursively(v);
            }
            if (schema.getItems() != null) stripNullableRecursively(schema.getItems());
            if (schema.getAdditionalProperties() != null) stripNullableRecursively(schema.getAdditionalProperties());
            if (schema.getAllOf() != null) schema.getAllOf().forEach(OpenApiConfig::stripNullableRecursively);
            if (schema.getAnyOf() != null) schema.getAnyOf().forEach(OpenApiConfig::stripNullableRecursively);
            if (schema.getOneOf() != null) schema.getOneOf().forEach(OpenApiConfig::stripNullableRecursively);
            return;
        }
        if (node instanceof OpenAPI api
                && api.getComponents() != null
                && api.getComponents().getSchemas() != null) {
            api.getComponents().getSchemas().values().forEach(OpenApiConfig::stripNullableRecursively);
            Paths paths = api.getPaths();
            if (paths != null) {
                for (PathItem item : paths.values()) {
                    item.readOperations().forEach(op -> {
                        if (op.getParameters() != null) {
                            op.getParameters().forEach(p -> {
                                if (p.getSchema() != null) stripNullableRecursively(p.getSchema());
                            });
                        }
                        if (op.getRequestBody() != null && op.getRequestBody().getContent() != null) {
                            op.getRequestBody().getContent().values().forEach(mt -> {
                                if (mt.getSchema() != null) stripNullableRecursively(mt.getSchema());
                            });
                        }
                        if (op.getResponses() != null) {
                            op.getResponses().values().forEach(resp -> {
                                if (resp.getContent() != null) {
                                    resp.getContent().values().forEach(mt -> {
                                        if (mt.getSchema() != null) stripNullableRecursively(mt.getSchema());
                                    });
                                }
                            });
                        }
                    });
                }
            }
        }
    }

    /**
     * Moves a shared path prefix (e.g. {@code /api/v1/}) from every path entry onto
     * {@code servers[0].url}. Downstream client generators (openapi-typescript, openapi-generator
     * go, the e2e Java client) treat server.url as the base URL and concatenate paths onto it;
     * leaving the prefix on every path makes the generated method names duplicate the prefix.
     */
    private OpenApiCustomizer stripPathPrefixCustomizer(String prefix) {
        return openApi -> {
            Paths paths = openApi.getPaths();
            if (paths == null) return;
            Paths rewritten = new Paths();
            rewritten.setExtensions(paths.getExtensions());
            for (var entry : paths.entrySet()) {
                String key = entry.getKey();
                PathItem item = entry.getValue();
                String newKey = key.startsWith(prefix) ? "/" + key.substring(prefix.length()) : key;
                rewritten.addPathItem(newKey, item);
            }
            openApi.setPaths(rewritten);
        };
    }

    /**
     * Public-API counterpart of {@link #referenceOperatorEventCustomizer()} — same fix, just for
     * the schemas the public spec actually contains (the tunnel-only families don't appear here).
     */
    private OpenApiCustomizer referencePublicPolymorphicFieldsCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) return;
            applyReplacement(openApi, "ResourceConfig", "source", "SourceConfig", false);
            applyReplacement(openApi, "GitSource", "buildConfig", "BuildConfig", false);
        };
    }

    /**
     * Springdoc inlines polymorphic field types as literal {@code oneOf} blocks even when the
     * abstract base type (e.g. {@code OperatorEvent}, {@code SourceConfig}, {@code BuildConfig})
     * is already a named schema. openapi-generator then emits synthetic wrapper types
     * ({@code PushEventsRequestEventsInner}, {@code ResourceConfigSource},
     * {@code GitSourceAllOfBuildConfig}, …) for those inline oneOfs — and, worse, the wrappers
     * collide badly with our schemaMappings that point oneOf subtypes at hand-written shared.crd
     * classes. Replace each inline oneOf with a {@code $ref} to the existing abstract-type
     * component so the generator uses the named polymorphic type directly.
     */
    private OpenApiCustomizer referenceOperatorEventCustomizer() {
        record Replacement(String parentSchema, String fieldName, String refTarget, boolean inArrayItems) {}
        var replacements = List.of(
                new Replacement("PushEventsRequest", "events", "OperatorEvent", true),
                new Replacement("ResourceConfig", "source", "SourceConfig", false),
                new Replacement("GitSource", "buildConfig", "BuildConfig", false));
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) return;
            for (var r : replacements) {
                applyReplacement(openApi, r.parentSchema, r.fieldName, r.refTarget, r.inArrayItems);
            }
        };
    }

    /**
     * Springdoc emits the polymorphic parent ({@code SourceConfig}, {@code BuildConfig}) as
     * {@code type: object} with a {@code type: string} property AND {@code oneOf} +
     * {@code discriminator}, and each subtype as {@code allOf: [$ref(parent), {inline}]}.
     * openapi-generator's Java template then makes the parent both an
     * {@code AbstractOpenApiSchema} oneOf wrapper AND a real superclass of every subtype, so
     * {@code new SourceConfig(new DockerSource())} serializes the wrapper's {@code instance} /
     * {@code isNullable} / {@code schemaType} fields onto the wire instead of the inner DTO.
     *
     * <p>This customizer cuts the inheritance leg: the parent loses {@code properties} /
     * {@code required} / {@code type:object} (becomes a pure {@code oneOf} + {@code discriminator}
     * wrapper) and each subtype's {@code allOf: [$ref, inline]} collapses to just the inline
     * branch (becomes a flat standalone schema). End result: openapi-generator emits a clean
     * oneOf wrapper plus unrelated DTOs, and the discriminator reaches the wire.
     */
    private OpenApiCustomizer flattenPolymorphicSubtypesCustomizer(String... parentNames) {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) return;
            var schemas = openApi.getComponents().getSchemas();
            for (String parentName : parentNames) {
                var parent = schemas.get(parentName);
                if (parent == null) continue;
                parent.setProperties(null);
                parent.setRequired(null);
                parent.setType(null);
                String parentRef = "#/components/schemas/" + parentName;
                for (var entry : schemas.entrySet()) {
                    var schema = entry.getValue();
                    if (schema.getAllOf() == null) continue;
                    var allOf = schema.getAllOf();
                    io.swagger.v3.oas.models.media.Schema<?> inline = null;
                    boolean refsParent = false;
                    for (Object branch : allOf) {
                        if (!(branch instanceof io.swagger.v3.oas.models.media.Schema<?> b)) continue;
                        if (parentRef.equals(b.get$ref())) {
                            refsParent = true;
                        } else if (b.get$ref() == null) {
                            inline = b;
                        }
                    }
                    if (refsParent && inline != null) {
                        schema.setAllOf(null);
                        schema.setType(inline.getType() != null ? inline.getType() : "object");
                        schema.setProperties(inline.getProperties());
                        // Discriminator validation requires the discriminator property to be in
                        // the `required` list on every subtype.
                        var required = inline.getRequired() != null
                                ? new java.util.ArrayList<>(inline.getRequired())
                                : new java.util.ArrayList<String>();
                        if (!required.contains("type")) required.add("type");
                        schema.setRequired(required);
                    }
                }
            }
        };
    }

    private static void applyReplacement(
            OpenAPI openApi, String parentSchema, String fieldName, String refTarget, boolean inArrayItems) {
        var parent = openApi.getComponents().getSchemas().get(parentSchema);
        if (parent == null) return;
        var holder = findField(parent, fieldName);
        if (holder == null) return;
        io.swagger.v3.oas.models.media.Schema<Object> ref = new io.swagger.v3.oas.models.media.Schema<>();
        ref.set$ref("#/components/schemas/" + refTarget);
        if (inArrayItems) {
            holder.setItems(ref);
        } else {
            holder.set$ref(ref.get$ref());
            holder.setOneOf(null);
        }
    }

    @SuppressWarnings("rawtypes")
    private static io.swagger.v3.oas.models.media.Schema<?> findField(
            io.swagger.v3.oas.models.media.Schema<?> schema, String fieldName) {
        if (schema.getProperties() != null && schema.getProperties().containsKey(fieldName)) {
            return (io.swagger.v3.oas.models.media.Schema<?>)
                    schema.getProperties().get(fieldName);
        }
        // GitSource is built via allOf: [$ref(SourceConfig), {properties: {...}}] — walk the
        // allOf list to find the inline shape.
        if (schema.getAllOf() != null) {
            for (Object branch : schema.getAllOf()) {
                if (branch instanceof io.swagger.v3.oas.models.media.Schema<?> branchSchema) {
                    var found = findField(branchSchema, fieldName);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    /**
     * The tunnel's {@code subscribeCommands} endpoint is declared as a {@code text/event-stream}
     * response — springdoc has no way to model the per-frame payload schemas, so the SSE event
     * DTOs ({@link eu.appbahn.platform.api.tunnel.HelloAck},
     * {@link eu.appbahn.platform.api.tunnel.ApplyResource}, etc.) never appear in
     * {@code components.schemas}. Force-register them so the operator's openapi-generator can
     * emit Java classes for them — same JSON shape, just exposed as named components.
     */
    private OpenApiCustomizer sseFrameSchemasCustomizer() {
        Class<?>[] frameTypes = {
            eu.appbahn.platform.api.tunnel.HelloAck.class,
            eu.appbahn.platform.api.tunnel.AdminConfigPush.class,
            eu.appbahn.platform.api.tunnel.QuotaRbacCachePush.class,
            eu.appbahn.platform.api.tunnel.ApplyResource.class,
            eu.appbahn.platform.api.tunnel.DeleteResource.class,
            eu.appbahn.platform.api.tunnel.ApplyNamespace.class,
            eu.appbahn.platform.api.tunnel.DeleteNamespace.class,
            eu.appbahn.platform.api.tunnel.SubscribeCommandsRequest.class,
            // Audit enums are inlined inside AuditLogEvent by default; promote them to
            // top-level components so the operator's generated client gets them as plain
            // top-level Java enums instead of nested AuditLogEvent.ActionEnum types.
            eu.appbahn.platform.api.AuditAction.class,
            eu.appbahn.platform.api.AuditDecision.class,
            eu.appbahn.platform.api.AuditTargetType.class,
            eu.appbahn.platform.api.AuditActorSource.class,
        };
        return openApi -> {
            if (openApi.getComponents() == null) return;
            for (Class<?> type : frameTypes) {
                ModelConverters.getInstance()
                        .readAll(new AnnotatedType(type))
                        .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
            }
        };
    }

    /**
     * {@link GlobalExceptionHandler} is a {@code @RestControllerAdvice} — springdoc
     * doesn't scan it, so {@link ErrorResponse} never gets referenced from an
     * operation. Force-register it so downstream clients keep the type.
     */
    private OpenApiCustomizer errorResponseSchemaCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) return;
            ModelConverters.getInstance()
                    .readAll(new AnnotatedType(ErrorResponse.class))
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
        };
    }

    private static final class QuantityAsStringConverter implements ModelConverter {
        @Override
        public io.swagger.v3.oas.models.media.Schema<?> resolve(
                AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
            if (isQuantity(type.getType())) {
                return new StringSchema();
            }
            return chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        }

        private static boolean isQuantity(java.lang.reflect.Type type) {
            Class<?> raw;
            if (type instanceof Class<?> c) {
                raw = c;
            } else if (type instanceof com.fasterxml.jackson.databind.JavaType jt) {
                raw = jt.getRawClass();
            } else {
                return false;
            }
            return Quantity.class.isAssignableFrom(raw);
        }
    }
}

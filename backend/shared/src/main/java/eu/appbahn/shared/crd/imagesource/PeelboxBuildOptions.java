package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Typed mirror of Peelbox's {@code UniversalBuild} schema (Peelbox 0.6.0 —
 * {@code crates/core/src/output/schema.rs}). One {@code PeelboxBuildOptions} describes a single
 * image build: metadata, build-stage packages/commands/cache, and runtime-stage
 * packages/copy/command/ports/env/health. Snake-case fields ({@code project_name},
 * {@code build_system}) are mapped via {@link JsonProperty} so the wire format matches
 * Peelbox's CLI output verbatim.
 *
 * <p>One Resource carries one build target; monorepos with multiple targets become separate
 * Resources.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PeelboxBuildOptions {

    private String version;
    private BuildMetadata metadata;
    private BuildStage build;
    private RuntimeStage runtime;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BuildMetadata {

        @JsonProperty("project_name")
        private String projectName;

        private String language;

        @JsonProperty("build_system")
        private String buildSystem;

        private String framework;
        private String reasoning;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BuildStage {
        private List<String> packages;
        private Map<String, String> env;
        private List<String> commands;
        private List<String> cache;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RuntimeStage {
        private List<String> packages;
        private Map<String, String> env;
        private List<CopySpec> copy;
        private List<String> command;
        private String workdir;
        private List<Integer> ports;
        private HealthCheck health;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CopySpec {
        private String from;
        private String to;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HealthCheck {
        private String endpoint;
    }
}

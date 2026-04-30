package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.crd.generator.annotation.PrinterColumn;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.Size;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceSpec {

    @PrinterColumn(name = "TYPE", priority = 1)
    @Required
    @Size(min = 1)
    private String type;

    @PrinterColumn(name = "NAME", priority = 2)
    @Required
    @Size(min = 1)
    private String name;

    @Required
    private String environmentId;

    @Required
    private String projectId;

    @Required
    private String workspaceId;

    private ResourceConfig config;
    private List<LinkConfig> links;

    /** When true, the operator removes the underlying workload (no Deployment exists). */
    private Boolean stopped;

    /**
     * @deprecated set {@link #release} instead. Bumped by the platform on each deployment
     * trigger to force K8s pod rollout — coexists with the new image-source-driven release path
     * while the platform API migrates to {@link #release}.
     */
    @Deprecated
    private String deploymentRevision;

    /**
     * Image-source-driven release. When {@link Release#getFromImageSource()} is set, the operator
     * resolves the bound sibling ImageSource and renders the pod template from its
     * {@code status.latestArtifact}. Mutually exclusive with the legacy {@link ResourceConfig}
     * source path; if both are present, {@code release} wins.
     */
    private Release release;

    /**
     * Bumped to force a re-roll without changing the underlying ImageSource. The operator
     * incorporates this into the pod-template hash so a bump triggers a new revision.
     */
    private Long restartGeneration;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LinkConfig {
        private String resource;
        private String secret;
        private Map<String, String> env;
    }
}

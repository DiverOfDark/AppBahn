package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import java.util.Map;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BuildPipelineSpec {
    private String versionPlaceholder;
    private String expectedVersion;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private PodSpec buildPodSpec;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> buildPodAnnotations;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> buildPodLabels;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private DeploymentSpec deploymentSpec;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> deploymentAnnotations;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> deploymentLabels;

    private String buildNonce;
}

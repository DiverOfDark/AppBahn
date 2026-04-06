package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import lombok.Data;

import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContinuousDeploymentPipelineSpec {
    private PodSpec buildPodSpec;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> buildPodAnnotations;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> buildPodLabels;
    private DeploymentSpec deploymentSpec;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> deploymentAnnotations;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> deploymentLabels;
    private String versionPlaceholder;
    private RepositoryConfig repositoryConfig;
    private String forceBuildNonce;
}

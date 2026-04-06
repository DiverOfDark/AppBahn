package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import eu.appbahn.shared.model.BuildPipelinePhase;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BuildPipelineStatus {
    private Long observedGeneration;
    private BuildPipelinePhase phase;
    private String builtVersion;
    private String failedForVersion;
    private String runningVersion;
    private List<BuildPipelineCondition> conditions;
    private String builtNonce;
}

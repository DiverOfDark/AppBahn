package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContinuousDeploymentPipelineStatus {
    private ZonedDateTime lastCheck;
    private String status;
    private String currentHead;
}

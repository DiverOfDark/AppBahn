package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.ZonedDateTime;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContinuousDeploymentPipelineStatus {
    private ZonedDateTime lastCheck;
    private String status;
    private String currentHead;
}

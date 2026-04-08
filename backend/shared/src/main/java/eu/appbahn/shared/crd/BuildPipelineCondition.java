package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import eu.appbahn.shared.model.ConditionReason;
import eu.appbahn.shared.model.ConditionStatus;
import eu.appbahn.shared.model.ConditionType;
import java.time.Instant;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BuildPipelineCondition {
    private ConditionType type;
    private ConditionStatus status;
    private ConditionReason reason;
    private String message;
    private Instant lastTransitionTime;
}

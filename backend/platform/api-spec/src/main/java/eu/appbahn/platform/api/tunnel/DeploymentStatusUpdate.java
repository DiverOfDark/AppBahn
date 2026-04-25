package eu.appbahn.platform.api.tunnel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.lang.Nullable;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeploymentStatusUpdate extends OperatorEvent {

    private String deploymentId;
    private String status;

    @Nullable
    private String message;

    public DeploymentStatusUpdate() {
        setType("deployment-status-update");
    }
}

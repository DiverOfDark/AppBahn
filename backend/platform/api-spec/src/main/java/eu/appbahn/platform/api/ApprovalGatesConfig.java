package eu.appbahn.platform.api;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class ApprovalGatesConfig {

    @Nullable
    private Boolean enabled;

    @Nullable
    private Integer requiredApprovals;
}

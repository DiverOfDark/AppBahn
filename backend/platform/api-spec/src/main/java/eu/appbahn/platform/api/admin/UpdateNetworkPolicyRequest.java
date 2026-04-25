package eu.appbahn.platform.api.admin;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class UpdateNetworkPolicyRequest {

    @Nullable
    private String name;

    @Nullable
    private String description;

    @Nullable
    private Object policy;
}

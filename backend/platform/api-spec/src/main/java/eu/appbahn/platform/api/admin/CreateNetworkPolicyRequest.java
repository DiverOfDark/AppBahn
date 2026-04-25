package eu.appbahn.platform.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class CreateNetworkPolicyRequest {

    @NotNull
    private String name;

    @Nullable
    private String description;

    @Valid
    @Nullable
    private UUID workspaceId;

    @NotNull
    private Object policy;
}

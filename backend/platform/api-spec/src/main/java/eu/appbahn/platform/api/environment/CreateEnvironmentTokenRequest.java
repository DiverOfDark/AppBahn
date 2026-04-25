package eu.appbahn.platform.api.environment;

import eu.appbahn.shared.model.MemberRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateEnvironmentTokenRequest {

    @NotNull
    private String name;

    @NotNull
    @Valid
    private MemberRole role;

    @NotNull
    private Integer expiresInDays;
}

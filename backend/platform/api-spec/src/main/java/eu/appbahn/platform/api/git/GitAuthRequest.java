package eu.appbahn.platform.api.git;

import eu.appbahn.platform.api.GitAuth;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class GitAuthRequest {

    @NotNull
    private String url;

    @Valid
    @Nullable
    private GitAuth auth;
}

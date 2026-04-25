package eu.appbahn.platform.api.git;

import eu.appbahn.platform.api.GitAuth;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class DetectBuildRequest {

    @NotNull
    private String url;

    @NotNull
    private String branch;

    @Nullable
    private String path;

    @Valid
    @Nullable
    private GitAuth auth;
}

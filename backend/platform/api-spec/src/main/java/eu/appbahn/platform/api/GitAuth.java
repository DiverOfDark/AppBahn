package eu.appbahn.platform.api;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class GitAuth {

    @NotNull
    private String type;

    @Nullable
    private String username;

    @Nullable
    private String password;

    @Nullable
    private String privateKey;
}

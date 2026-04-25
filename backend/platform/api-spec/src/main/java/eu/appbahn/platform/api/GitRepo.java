package eu.appbahn.platform.api;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class GitRepo {

    @Nullable
    private String name;

    @Nullable
    private String url;

    @Nullable
    private String defaultBranch;
}

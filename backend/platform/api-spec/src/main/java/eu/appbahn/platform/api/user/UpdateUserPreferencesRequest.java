package eu.appbahn.platform.api.user;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class UpdateUserPreferencesRequest {

    @Nullable
    private String defaultWorkspaceSlug;
}

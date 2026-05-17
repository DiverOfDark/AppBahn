package eu.appbahn.platform.api.user;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class UserPreferences {

    @Nullable
    private String defaultWorkspaceSlug;
}

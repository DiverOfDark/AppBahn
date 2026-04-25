package eu.appbahn.platform.api;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class PlatformConfigBranding {

    @Nullable
    private String instanceName;

    @Nullable
    private String tagline;

    @Nullable
    private String logoUrl;

    @Nullable
    private String loginButtonText;
}

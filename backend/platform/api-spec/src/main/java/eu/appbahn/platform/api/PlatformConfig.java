package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class PlatformConfig {

    @Nullable
    private String domain;

    @Nullable
    private String namespacePrefix;

    @Valid
    @Nullable
    private RegistryConfig registry;

    @Valid
    @Nullable
    private PlatformConfigBranding branding;

    @Nullable
    private Object auth;

    @Valid
    @Nullable
    private Quota defaultQuota;

    @Nullable
    private Object buildConfig;
}

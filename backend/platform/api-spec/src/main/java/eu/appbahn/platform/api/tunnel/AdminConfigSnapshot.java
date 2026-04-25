package eu.appbahn.platform.api.tunnel;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class AdminConfigSnapshot {

    @Nullable
    private String baseDomain;

    @Nullable
    private String registryUrl;

    @Nullable
    private String registryRepositoryPrefix;

    @Nullable
    private String namespacePrefix;
}

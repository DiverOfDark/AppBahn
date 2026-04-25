package eu.appbahn.platform.api;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class ResourceTypeInfo {

    @Nullable
    private String type;

    @Nullable
    private String displayName;

    @Nullable
    private String description;

    @Nullable
    private ResourceCategory category;

    @Nullable
    private Object configSchema;

    @Nullable
    private Boolean available;
}

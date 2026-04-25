package eu.appbahn.platform.api.resource;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class ResourceCreatedResponse {

    @Nullable
    private String slug;

    @Nullable
    private String environmentSlug;
}

package eu.appbahn.platform.api.tunnel;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class EnvironmentEntry {

    private String slug;
    private String namespace;

    /** Per-dimension caps. A dimension of 0 at this level means "no cap here". */
    @Valid
    @Nullable
    private QuotaDimensions limits;

    /** Per-env current usage (sum over resource_cache rows; replicas multiplier applied). */
    @Valid
    @Nullable
    private QuotaDimensions current;

    private List<String> allowedUserSubjects = new ArrayList<>();
    private List<String> allowedOidcGroups = new ArrayList<>();

    @Nullable
    private String projectSlug;

    @Nullable
    private String workspaceSlug;
}

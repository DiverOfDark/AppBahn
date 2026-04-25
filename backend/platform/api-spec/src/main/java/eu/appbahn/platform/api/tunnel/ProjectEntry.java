package eu.appbahn.platform.api.tunnel;

import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class ProjectEntry {

    private String slug;

    @Nullable
    private String workspaceSlug;

    @Valid
    @Nullable
    private QuotaDimensions limits;
}

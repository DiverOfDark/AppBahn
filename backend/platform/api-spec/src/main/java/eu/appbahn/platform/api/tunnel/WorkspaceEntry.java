package eu.appbahn.platform.api.tunnel;

import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class WorkspaceEntry {

    private String slug;

    @Valid
    @Nullable
    private QuotaDimensions limits;
}

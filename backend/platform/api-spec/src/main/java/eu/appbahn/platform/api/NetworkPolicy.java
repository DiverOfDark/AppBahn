package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class NetworkPolicy {

    @Valid
    @Nullable
    private UUID id;

    @Nullable
    private String name;

    @Nullable
    private String description;

    @Valid
    @Nullable
    private UUID workspaceId;

    @Nullable
    private Object policy;
}

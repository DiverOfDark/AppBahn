package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class ResourceTypeDefinition {

    @Nullable
    private String type;

    @Nullable
    private Object definition;

    @Nullable
    private Object adminConfig;

    @Valid
    @Nullable
    private OffsetDateTime lastSyncedAt;
}

package eu.appbahn.platform.api;

import eu.appbahn.shared.crd.ResourcePhase;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class Resource {

    @Nullable
    private String slug;

    @Nullable
    private String name;

    @Nullable
    private String type;

    @Nullable
    private String environmentSlug;

    @Valid
    @Nullable
    private eu.appbahn.shared.crd.ResourceConfig config;

    @Valid
    private List<eu.appbahn.shared.crd.ResourceSpec.LinkConfig> links = new ArrayList<>();

    @Nullable
    private ResourcePhase status;

    @Valid
    @Nullable
    private eu.appbahn.shared.crd.ResourceStatusDetail statusDetail;

    @Valid
    @Nullable
    private OffsetDateTime lastSyncedAt;

    @Valid
    @Nullable
    private OffsetDateTime createdAt;

    @Valid
    @Nullable
    private OffsetDateTime updatedAt;
}

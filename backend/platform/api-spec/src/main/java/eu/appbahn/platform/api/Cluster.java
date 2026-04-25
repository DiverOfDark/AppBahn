package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class Cluster {

    @Nullable
    private String name;

    @Nullable
    private String description;

    @Nullable
    private String kubeconfigSecret;

    @Valid
    @Nullable
    private OffsetDateTime createdAt;
}

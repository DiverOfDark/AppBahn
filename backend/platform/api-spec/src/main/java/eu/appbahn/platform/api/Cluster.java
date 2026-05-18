package eu.appbahn.platform.api;

import eu.appbahn.shared.crd.ClusterConfig;
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

    /** Admin-managed cluster config (node-pool catalogue, …). */
    @Valid
    @Nullable
    private ClusterConfig config;
}

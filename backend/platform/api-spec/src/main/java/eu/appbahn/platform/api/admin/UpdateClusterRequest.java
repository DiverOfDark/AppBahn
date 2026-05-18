package eu.appbahn.platform.api.admin;

import eu.appbahn.shared.crd.ClusterConfig;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class UpdateClusterRequest {

    @Nullable
    private String description;

    /** Replace the cluster's admin-managed config. Null means "leave config untouched". */
    @Valid
    @Nullable
    private ClusterConfig config;
}

package eu.appbahn.platform.api.tunnel;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Carried as query params on the SSE {@code GET /api/tunnel/v1/commands} stream open.
 * {@code lastAdminConfigRevision} / {@code lastQuotaRbacRevision} let the platform skip
 * initial snapshots the operator already has — zero means "no prior state".
 */
@Data
public class SubscribeCommandsRequest {

    @NotBlank
    private String clusterName;

    @Nullable
    private String operatorInstanceId;

    @Nullable
    private String operatorVersion;

    private long lastAdminConfigRevision;

    private long lastQuotaRbacRevision;
}

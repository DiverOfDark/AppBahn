package eu.appbahn.platform.api.tunnel;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Unauthenticated bootstrap call from the operator. The platform UPSERTs the cluster row by
 * name and runs auto-approval against the published public key. Idempotent: operator sends
 * it on every startup.
 */
@Data
public class RegisterClusterRequest {

    @NotBlank
    private String clusterName;

    /** Operator's RSA public key in PEM. The platform stores this and uses it to verify JWTs. */
    @NotBlank
    private String publicKey;

    @Nullable
    private String operatorVersion;

    @Nullable
    private String operatorInstanceId;
}

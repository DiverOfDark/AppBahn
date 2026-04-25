package eu.appbahn.platform.api.tunnel;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class RegisterClusterAck {

    private String clusterName;

    /** ClusterStatus string: PENDING / APPROVED / REVOKED. */
    private String status;

    /** SHA-256 of the stored public key, hex. */
    @Nullable
    private String publicKeyFingerprint;
}

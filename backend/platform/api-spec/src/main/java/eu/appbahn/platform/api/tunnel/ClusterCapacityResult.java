package eu.appbahn.platform.api.tunnel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.lang.Nullable;

/**
 * Cluster-wide CPU + memory headroom returned by the operator in response to
 * {@link QueryClusterCapacity}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ClusterCapacityResult extends CommandResponsePayload {

    @Nullable
    private Long cpuAvailableMillicores;

    @Nullable
    private Long cpuTotalMillicores;

    @Nullable
    private Long memoryAvailableBytes;

    @Nullable
    private Long memoryTotalBytes;

    @Nullable
    private Integer schedulableNodes;

    public ClusterCapacityResult() {
        setType("cluster-capacity-result");
    }
}

package eu.appbahn.platform.api;

import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Aggregate CPU + memory headroom across all schedulable nodes in a cluster, as observed by
 * the operator. Drives the Scale modal's "cluster has X vCPU / Y GB free" preview so the
 * user can tell at a glance whether a scale-up request will fit.
 *
 * <p>The arithmetic is straightforward: {@code available = sum(node.allocatable) -
 * sum(pod.requests across all running pods)}. We use requests, not usage — this is the
 * scheduler's view of the cluster, and it's what determines whether a new pod with given
 * requests will actually be admitted. {@code total} sums node {@code allocatable} only.
 *
 * <p>Nodes marked unschedulable (cordoned) are skipped on both sides of the subtraction.
 */
@Data
public class ClusterCapacity {

    @Nullable
    private String clusterName;

    /** Free CPU after subtracting all current pod requests, in millicores. */
    @Nullable
    private Long cpuAvailableMillicores;

    /** Sum of node-allocatable CPU across schedulable nodes, in millicores. */
    @Nullable
    private Long cpuTotalMillicores;

    /** Free memory after subtracting all current pod requests, in bytes. */
    @Nullable
    private Long memoryAvailableBytes;

    /** Sum of node-allocatable memory across schedulable nodes, in bytes. */
    @Nullable
    private Long memoryTotalBytes;

    /** Schedulable node count (cordoned nodes excluded). */
    @Nullable
    private Integer schedulableNodes;
}

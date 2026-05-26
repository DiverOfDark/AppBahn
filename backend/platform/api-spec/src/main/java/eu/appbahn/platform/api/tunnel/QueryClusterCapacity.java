package eu.appbahn.platform.api.tunnel;

import lombok.Data;

/**
 * Platform→operator query: sum allocatable CPU + memory across all schedulable nodes and
 * subtract sum of current pod requests so the platform can show "cluster has X free" in
 * the Scale modal. Reply lands on the ack endpoint as a {@link ClusterCapacityResult}
 * payload.
 */
@Data
public class QueryClusterCapacity {

    public static final String EVENT_NAME = "query-cluster-capacity";

    private String correlationId;
}

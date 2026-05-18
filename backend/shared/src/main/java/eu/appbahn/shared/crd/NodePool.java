package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * A named pool of cluster nodes — typically backing a specific machine type (general, cpu-optimized,
 * gpu-l4, …). Each pool carries a {@link #nodeSelector} and {@link #tolerations} that the operator
 * stamps onto pod templates when a resource's {@link ResourceConfig.HostingConfig#nodePool} points
 * at this pool's {@link #name}. The catalogue lives on the cluster (see {@link ClusterConfig}); the
 * SPA reads it to populate the resource-creation node-pool picker.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodePool {

    private String name;
    private String displayName;
    private Map<String, String> nodeSelector;
    private List<Toleration> tolerations;
}

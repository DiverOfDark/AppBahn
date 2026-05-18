package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Data;

/**
 * Per-cluster configuration administered by the platform admin. Carries the catalogue of
 * {@link NodePool}s the cluster exposes — the SPA reads this to populate the node-pool picker on
 * the resource-creation form, and the operator reads it (via the admin-config push) to look up
 * the selector + tolerations to stamp onto a pod template when a resource pins a {@code nodePool}.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClusterConfig {

    private List<NodePool> nodePools;
}

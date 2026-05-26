package eu.appbahn.platform.tunnel.rpc;

import eu.appbahn.platform.api.ClusterCapacity;
import eu.appbahn.platform.api.cluster.ClustersApi;
import eu.appbahn.platform.common.security.AuthContextHolder;
import eu.appbahn.platform.workspace.service.ClusterCapacitySupplier;
import eu.appbahn.platform.workspace.service.ClusterLivenessProbe;
import eu.appbahn.platform.workspace.service.ClusterPermissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ClustersController implements ClustersApi {

    private final ClusterPermissionService clusterPermissionService;
    private final ClusterLivenessProbe clusterLivenessProbe;
    private final ClusterCapacitySupplier capacitySupplier;

    public ClustersController(
            ClusterPermissionService clusterPermissionService,
            ClusterLivenessProbe clusterLivenessProbe,
            ClusterCapacitySupplier capacitySupplier) {
        this.clusterPermissionService = clusterPermissionService;
        this.clusterLivenessProbe = clusterLivenessProbe;
        this.capacitySupplier = capacitySupplier;
    }

    @Override
    public ResponseEntity<ClusterCapacity> getClusterCapacity(String slug) {
        clusterPermissionService.requireClusterReadAccess(AuthContextHolder.get(), slug);
        clusterLivenessProbe.requireReachable(slug);
        return ResponseEntity.ok(capacitySupplier.fetch(slug));
    }
}

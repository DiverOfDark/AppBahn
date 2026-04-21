package eu.appbahn.platform.tunnel.cluster;

import eu.appbahn.platform.common.exception.ValidationException;
import eu.appbahn.platform.workspace.service.TargetClusterResolver;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TunnelTargetClusterResolver implements TargetClusterResolver {

    private final ClusterRepository clusterRepository;

    public TunnelTargetClusterResolver(ClusterRepository clusterRepository) {
        this.clusterRepository = clusterRepository;
    }

    @Override
    public String resolve(String requested) {
        if (requested != null && !requested.isBlank()) {
            ClusterEntity cluster = clusterRepository
                    .findById(requested)
                    .orElseThrow(() -> new ValidationException("Target cluster '" + requested + "' is not registered"));
            if (cluster.getStatus() != ClusterStatus.APPROVED) {
                throw new ValidationException(
                        "Target cluster '" + requested + "' is not approved (status: " + cluster.getStatus() + ")");
            }
            return cluster.getName();
        }

        List<ClusterEntity> approved = clusterRepository.findAllByStatus(ClusterStatus.APPROVED);
        if (approved.isEmpty()) {
            throw new ValidationException(
                    "No approved cluster registered — register one via the operator and approve it in /admin/clusters");
        }
        if (approved.size() > 1) {
            String names = approved.stream().map(ClusterEntity::getName).collect(Collectors.joining(", "));
            throw new ValidationException(
                    "Multiple approved clusters registered; specify targetCluster (one of: " + names + ")");
        }
        return approved.get(0).getName();
    }
}

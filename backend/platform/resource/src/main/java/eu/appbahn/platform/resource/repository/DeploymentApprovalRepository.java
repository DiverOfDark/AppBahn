package eu.appbahn.platform.resource.repository;

import eu.appbahn.platform.resource.entity.DeploymentApprovalEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentApprovalRepository extends JpaRepository<DeploymentApprovalEntity, UUID> {

    List<DeploymentApprovalEntity> findByDeploymentId(UUID deploymentId);
}

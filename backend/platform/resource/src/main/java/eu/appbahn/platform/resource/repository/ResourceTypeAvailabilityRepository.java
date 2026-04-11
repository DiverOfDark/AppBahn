package eu.appbahn.platform.resource.repository;

import eu.appbahn.platform.resource.entity.ResourceTypeAvailabilityEntity;
import eu.appbahn.platform.resource.entity.ResourceTypeAvailabilityId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceTypeAvailabilityRepository
        extends JpaRepository<ResourceTypeAvailabilityEntity, ResourceTypeAvailabilityId> {

    List<ResourceTypeAvailabilityEntity> findByClusterName(String clusterName);

    List<ResourceTypeAvailabilityEntity> findByTypeAndAvailableTrue(String type);
}

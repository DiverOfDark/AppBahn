package eu.appbahn.platform.tunnel.cluster;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClusterRepository extends JpaRepository<ClusterEntity, String> {

    List<ClusterEntity> findAllByStatus(ClusterStatus status);
}

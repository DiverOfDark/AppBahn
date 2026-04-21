package eu.appbahn.platform.tunnel.cluster;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClusterSessionRepository extends JpaRepository<ClusterSessionEntity, String> {}

package eu.appbahn.platform.tunnel.command;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FullSyncChunkBufferRepository
        extends JpaRepository<FullSyncChunkBufferEntity, FullSyncChunkBufferEntity.Pk> {

    List<FullSyncChunkBufferEntity> findByIdClusterNameAndIdSyncSessionIdOrderByIdChunkIndex(
            String clusterName, UUID syncSessionId);

    long deleteByIdClusterNameAndIdSyncSessionId(String clusterName, UUID syncSessionId);

    long deleteByReceivedAtBefore(Instant cutoff);
}

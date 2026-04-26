package eu.appbahn.platform.tunnel.command;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PendingCommandRepository extends JpaRepository<PendingCommandEntity, UUID> {

    Optional<PendingCommandEntity> findByCorrelationId(UUID correlationId);

    @Query(value = """
                    SELECT * FROM pending_command
                     WHERE cluster_name = :clusterName
                       AND acked_at IS NULL
                       AND (claimed_by_replica IS NULL OR claimed_at < :staleBefore)
                     ORDER BY enqueued_at
                     LIMIT :limit
                     FOR UPDATE SKIP LOCKED
                    """, nativeQuery = true)
    List<PendingCommandEntity> findClaimable(
            @Param("clusterName") String clusterName,
            @Param("staleBefore") Instant staleBefore,
            @Param("limit") int limit);

    List<PendingCommandEntity> findByAckedAtIsNullAndExpiresAtBefore(Instant cutoff);

    @Modifying
    @Query("DELETE FROM PendingCommandEntity c WHERE c.ackedAt IS NOT NULL AND c.ackedAt < :cutoff")
    int deleteAckedBefore(@Param("cutoff") Instant cutoff);
}

package eu.appbahn.platform.common.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface IdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecordEntity, IdempotencyRecordEntity.IdempotencyRecordId> {

    Optional<IdempotencyRecordEntity> findByIdempotencyKeyAndActorId(String idempotencyKey, UUID actorId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM idempotency_record WHERE created_at < :cutoff", nativeQuery = true)
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}

package eu.appbahn.platform.workspace.repository;

import eu.appbahn.platform.workspace.entity.EnvironmentTokenEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentTokenRepository extends JpaRepository<EnvironmentTokenEntity, UUID> {

    List<EnvironmentTokenEntity> findByEnvironmentId(UUID environmentId);

    Optional<EnvironmentTokenEntity> findByTokenHash(String tokenHash);
}

package eu.appbahn.platform.workspace.repository;

import eu.appbahn.platform.workspace.entity.EnvironmentMemberOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EnvironmentMemberOverrideRepository extends JpaRepository<EnvironmentMemberOverrideEntity, EnvironmentMemberOverrideEntity.EnvironmentMemberOverrideId> {

    Optional<EnvironmentMemberOverrideEntity> findByEnvironmentIdAndUserId(UUID environmentId, UUID userId);
}

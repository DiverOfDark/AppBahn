package eu.appbahn.platform.workspace.repository;

import eu.appbahn.platform.workspace.entity.ProjectMemberOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberOverrideRepository extends JpaRepository<ProjectMemberOverrideEntity, ProjectMemberOverrideEntity.ProjectMemberOverrideId> {

    Optional<ProjectMemberOverrideEntity> findByProjectIdAndUserId(UUID projectId, UUID userId);
}

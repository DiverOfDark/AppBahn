package eu.appbahn.platform.workspace.repository;

import eu.appbahn.platform.workspace.entity.ProjectMemberOverrideEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberOverrideRepository
        extends JpaRepository<ProjectMemberOverrideEntity, ProjectMemberOverrideEntity.ProjectMemberOverrideId> {

    Optional<ProjectMemberOverrideEntity> findByProjectIdAndUserId(UUID projectId, UUID userId);

    List<ProjectMemberOverrideEntity> findByProjectId(UUID projectId);
}

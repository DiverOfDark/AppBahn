package eu.appbahn.platform.workspace.repository;

import eu.appbahn.platform.workspace.entity.ProjectEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    Optional<ProjectEntity> findBySlug(String slug);

    Page<ProjectEntity> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    List<ProjectEntity> findByWorkspaceId(UUID workspaceId);

    boolean existsByWorkspaceId(UUID workspaceId);
}

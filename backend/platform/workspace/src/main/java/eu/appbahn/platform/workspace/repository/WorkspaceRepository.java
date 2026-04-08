package eu.appbahn.platform.workspace.repository;

import eu.appbahn.platform.workspace.entity.WorkspaceEntity;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {

    Optional<WorkspaceEntity> findBySlug(String slug);

    Page<WorkspaceEntity> findAllByIdIn(Collection<UUID> ids, Pageable pageable);
}

package eu.appbahn.platform.workspace.repository;

import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnvironmentRepository extends JpaRepository<EnvironmentEntity, UUID> {

    Optional<EnvironmentEntity> findBySlug(String slug);

    Page<EnvironmentEntity> findByProjectId(UUID projectId, Pageable pageable);

    List<EnvironmentEntity> findByProjectId(UUID projectId);

    boolean existsByProjectId(UUID projectId);
}

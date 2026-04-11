package eu.appbahn.platform.workspace.repository;

import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentRepository extends JpaRepository<EnvironmentEntity, UUID> {

    Optional<EnvironmentEntity> findBySlug(String slug);

    Page<EnvironmentEntity> findByProjectId(UUID projectId, Pageable pageable);

    List<EnvironmentEntity> findByProjectId(UUID projectId);

    boolean existsByProjectId(UUID projectId);

    List<EnvironmentEntity> findByProjectIdIn(List<UUID> projectIds);

    List<EnvironmentEntity> findByTargetCluster(String targetCluster);
}

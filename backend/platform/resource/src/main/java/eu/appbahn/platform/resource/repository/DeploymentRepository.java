package eu.appbahn.platform.resource.repository;

import eu.appbahn.platform.resource.entity.DeploymentEntity;
import eu.appbahn.shared.crd.DeploymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeploymentRepository extends JpaRepository<DeploymentEntity, UUID> {

    Page<DeploymentEntity> findByResourceSlug(String resourceSlug, Pageable pageable);

    Optional<DeploymentEntity> findByIdAndResourceSlug(UUID id, String resourceSlug);

    Optional<DeploymentEntity> findByResourceSlugAndPrimaryTrue(String resourceSlug);

    Optional<DeploymentEntity> findTopByResourceSlugOrderByCreatedAtDesc(String resourceSlug);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE DeploymentEntity d SET d.primary = false WHERE d.resourceSlug = :slug AND d.primary = true")
    void clearPrimary(@Param("slug") String resourceSlug);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE DeploymentEntity d SET d.primary = (d.id = :newPrimaryId) "
            + "WHERE d.resourceSlug = :slug AND (d.primary = true OR d.id = :newPrimaryId)")
    void transferPrimary(@Param("slug") String resourceSlug, @Param("newPrimaryId") UUID newPrimaryId);

    long countByResourceSlug(String resourceSlug);

    List<DeploymentEntity> findByResourceSlugAndStatus(String resourceSlug, DeploymentStatus status);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM DeploymentEntity d WHERE d.resourceSlug = :slug AND d.status = :status")
    void deleteByResourceSlugAndStatus(@Param("slug") String resourceSlug, @Param("status") DeploymentStatus status);
}

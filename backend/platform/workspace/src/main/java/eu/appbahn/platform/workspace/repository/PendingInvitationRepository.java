package eu.appbahn.platform.workspace.repository;

import eu.appbahn.platform.workspace.entity.PendingInvitationEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingInvitationRepository extends JpaRepository<PendingInvitationEntity, UUID> {

    List<PendingInvitationEntity> findByWorkspaceId(UUID workspaceId);

    Optional<PendingInvitationEntity> findByWorkspaceIdAndEmail(UUID workspaceId, String email);

    List<PendingInvitationEntity> findByEmail(String email);

    void deleteByCreatedAtBefore(Instant cutoff);
}

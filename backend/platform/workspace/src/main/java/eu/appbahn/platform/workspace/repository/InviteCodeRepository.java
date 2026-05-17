package eu.appbahn.platform.workspace.repository;

import eu.appbahn.platform.workspace.entity.InviteCodeEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteCodeRepository extends JpaRepository<InviteCodeEntity, UUID> {

    Optional<InviteCodeEntity> findByCode(String code);

    List<InviteCodeEntity> findByWorkspaceId(UUID workspaceId);
}

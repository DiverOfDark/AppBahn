package eu.appbahn.platform.workspace.repository;

import eu.appbahn.platform.workspace.entity.WorkspaceMemberEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository
        extends JpaRepository<WorkspaceMemberEntity, WorkspaceMemberEntity.WorkspaceMemberId> {

    List<WorkspaceMemberEntity> findByWorkspaceId(UUID workspaceId);

    Optional<WorkspaceMemberEntity> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<WorkspaceMemberEntity> findByUserId(UUID userId);
}

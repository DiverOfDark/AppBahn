package eu.appbahn.platform.workspace.repository;

import eu.appbahn.platform.workspace.entity.OidcGroupMappingEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OidcGroupMappingRepository extends JpaRepository<OidcGroupMappingEntity, UUID> {

    List<OidcGroupMappingEntity> findByWorkspaceId(UUID workspaceId);

    List<OidcGroupMappingEntity> findByWorkspaceIdAndOidcGroupIn(UUID workspaceId, Collection<String> groups);
}

package eu.appbahn.platform.workspace.entity;

import eu.appbahn.platform.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "oidc_group_mapping")
public class OidcGroupMappingEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "oidc_group", nullable = false)
    private String oidcGroup;

    @Column(nullable = false)
    private String role;
}

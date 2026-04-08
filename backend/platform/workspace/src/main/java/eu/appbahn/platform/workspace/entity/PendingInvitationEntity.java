package eu.appbahn.platform.workspace.entity;

import eu.appbahn.platform.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pending_invitation")
public class PendingInvitationEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String role;

    @Column(name = "invited_by")
    private UUID invitedBy;
}

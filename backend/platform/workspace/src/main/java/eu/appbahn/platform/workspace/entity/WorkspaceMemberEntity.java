package eu.appbahn.platform.workspace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "workspace_member")
@IdClass(WorkspaceMemberEntity.WorkspaceMemberId.class)
public class WorkspaceMemberEntity {

    @Id
    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String role;

    @Data
    public static class WorkspaceMemberId implements Serializable {

        private UUID workspaceId;
        private UUID userId;

        public WorkspaceMemberId() {}

        public WorkspaceMemberId(UUID workspaceId, UUID userId) {
            this.workspaceId = workspaceId;
            this.userId = userId;
        }
    }
}

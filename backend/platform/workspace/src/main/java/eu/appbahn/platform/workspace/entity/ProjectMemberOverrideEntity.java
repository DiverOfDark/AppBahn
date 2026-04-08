package eu.appbahn.platform.workspace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "project_member_override")
@IdClass(ProjectMemberOverrideEntity.ProjectMemberOverrideId.class)
public class ProjectMemberOverrideEntity {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String role;

    @Data
    public static class ProjectMemberOverrideId implements Serializable {
        private UUID projectId;
        private UUID userId;
    }
}

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
@Table(name = "environment_member_override")
@IdClass(EnvironmentMemberOverrideEntity.EnvironmentMemberOverrideId.class)
public class EnvironmentMemberOverrideEntity {

    @Id
    @Column(name = "environment_id")
    private UUID environmentId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String role;

    @Data
    public static class EnvironmentMemberOverrideId implements Serializable {
        private UUID environmentId;
        private UUID userId;
    }
}

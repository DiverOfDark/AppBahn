package eu.appbahn.platform.workspace.entity;

import eu.appbahn.platform.api.model.ApprovalGatesConfig;
import eu.appbahn.platform.api.model.Quota;
import eu.appbahn.platform.api.model.RegistryConfig;
import eu.appbahn.platform.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "environment")
public class EnvironmentEntity extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 18)
    private String slug;

    private String description;

    @Column(name = "target_cluster", nullable = false)
    private String targetCluster;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Quota quota;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private RegistryConfig registry;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "approval_gates", columnDefinition = "jsonb")
    private ApprovalGatesConfig approvalGates;
}

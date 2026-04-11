package eu.appbahn.platform.resource.entity;

import eu.appbahn.shared.crd.ResourceTypeAdminConfig;
import eu.appbahn.shared.crd.ResourceTypeDefinitionSpec;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "resource_type_definition")
public class ResourceTypeDefinitionEntity {

    @Id
    @Column(length = 63)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private ResourceTypeDefinitionSpec definition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "admin_config", columnDefinition = "jsonb", nullable = false)
    private ResourceTypeAdminConfig adminConfig;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}

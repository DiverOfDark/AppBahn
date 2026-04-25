package eu.appbahn.platform.resource.entity;

import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceSpec;
import eu.appbahn.shared.crd.ResourceStatusDetail;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "resource_cache")
public class ResourceCacheEntity {

    @Id
    @Column(length = 18)
    private String slug;

    @Column(name = "environment_id", nullable = false)
    private UUID environmentId;

    @Column(nullable = false)
    private String name;

    @Column(length = 63, nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private ResourceConfig config;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<ResourceSpec.LinkConfig> links = List.of();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ResourcePhase status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "status_detail", columnDefinition = "jsonb")
    private ResourceStatusDetail statusDetail;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic lock version. Hibernate increments this on every update and fails with
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException} if another
     * transaction wrote concurrently. Used by {@code ResourceService.create} to retry the
     * upsert when operator sync races platform create on the same slug.
     */
    @Version
    @Column(nullable = false)
    private Long version;
}

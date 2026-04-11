package eu.appbahn.platform.resource.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "resource_type_availability")
@IdClass(ResourceTypeAvailabilityId.class)
public class ResourceTypeAvailabilityEntity {

    @Id
    @Column(length = 63)
    private String type;

    @Id
    @Column(name = "cluster_name", length = 63)
    private String clusterName;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}

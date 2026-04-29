package eu.appbahn.platform.resource.entity;

import eu.appbahn.shared.crd.imagesource.ImageSourceSpec;
import eu.appbahn.shared.crd.imagesource.ImageSourceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "image_source_cache")
public class ImageSourceCacheEntity {

    @Id
    @Column(length = 63)
    private String slug;

    /** Environment whose namespace contains this ImageSource. May be null if the CR sits outside any env namespace. */
    @Column(name = "environment_id")
    private UUID environmentId;

    @Column(length = 253)
    private String namespace;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private ImageSourceSpec spec;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ImageSourceStatus status;

    /** Denormalised pointer to {@code status.observedCommit} for cheap filtering. */
    @Column(name = "observed_commit", length = 64)
    private String observedCommit;

    /** Denormalised pointer to {@code status.latestArtifact.imageRef} for cheap filtering. */
    @Column(name = "image_ref", columnDefinition = "TEXT")
    private String imageRef;

    @Column(name = "last_polled_at")
    private Instant lastPolledAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}

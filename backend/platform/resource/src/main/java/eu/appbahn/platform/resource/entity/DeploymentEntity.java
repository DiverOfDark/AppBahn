package eu.appbahn.platform.resource.entity;

import eu.appbahn.platform.api.TriggerType;
import eu.appbahn.shared.crd.DeploymentStatus;
import eu.appbahn.shared.crd.imagesource.BuildLifecycle;
import eu.appbahn.shared.util.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "deployment")
public class DeploymentEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "resource_slug", length = 18, nullable = false)
    private String resourceSlug;

    @Column(name = "environment_id", nullable = false)
    private UUID environmentId;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "image_ref")
    private String imageRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", length = 20, nullable = false)
    private TriggerType triggeredBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private DeploymentStatus status;

    /**
     * ImageSource-driven build lifecycle. Populated on rows minted by
     * {@code BuildLifecycleEvent}; null for legacy resource-driven deployments. Replaces
     * {@link #status} entirely once the Resource layer migrates to the new model.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle", length = 20)
    private BuildLifecycle lifecycle;

    @Column(name = "image_source_name", length = 255)
    private String imageSourceName;

    @Column(name = "image_source_namespace", length = 63)
    private String imageSourceNamespace;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "source_deployment_id")
    private UUID sourceDeploymentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UuidV7.generate();
        }
        var now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

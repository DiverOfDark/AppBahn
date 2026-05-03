package eu.appbahn.platform.resource.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Capability-token row that authenticates an inbound webhook to a specific ImageSource. Each
 * row binds one opaque random token to one ImageSource slug; the token is the entire auth
 * signal — provider-agnostic, no HMAC, no signature parsing.
 */
@Getter
@Setter
@Entity
@Table(name = "image_source_webhook_token")
public class ImageSourceWebhookTokenEntity {

    @Id
    @Column(length = 64)
    private String token;

    @Column(name = "image_source_slug", nullable = false, length = 63)
    private String imageSourceSlug;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

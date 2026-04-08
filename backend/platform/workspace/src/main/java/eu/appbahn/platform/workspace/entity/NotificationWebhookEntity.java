package eu.appbahn.platform.workspace.entity;

import eu.appbahn.platform.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "notification_webhook")
public class NotificationWebhookEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String url;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String events;

    private String secret;

    @Column(name = "created_by")
    private UUID createdBy;
}

package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class NotificationWebhook {

    @Valid
    @Nullable
    private UUID id;

    @Valid
    @Nullable
    private UUID workspaceId;

    @Nullable
    private String name;

    @Nullable
    private String url;

    @Valid
    private List<WebhookEvent> events = new ArrayList<>();

    @Valid
    @Nullable
    private UUID createdBy;
}

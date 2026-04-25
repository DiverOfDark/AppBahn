package eu.appbahn.platform.api.workspace;

import eu.appbahn.platform.api.WebhookEvent;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class UpdateNotificationWebhookRequest {

    @Nullable
    private String name;

    @Nullable
    private String url;

    @Valid
    private List<WebhookEvent> events = new ArrayList<>();
}

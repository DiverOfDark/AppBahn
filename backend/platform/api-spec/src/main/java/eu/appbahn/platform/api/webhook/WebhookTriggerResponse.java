package eu.appbahn.platform.api.webhook;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class WebhookTriggerResponse {

    @Nullable
    private Boolean changed;

    @Valid
    @Nullable
    private UUID deploymentId;
}

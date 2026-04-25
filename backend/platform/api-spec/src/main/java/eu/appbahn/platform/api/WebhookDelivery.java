package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class WebhookDelivery {

    @Valid
    @Nullable
    private UUID id;

    @Valid
    @Nullable
    private UUID webhookId;

    @Nullable
    private String event;

    @Nullable
    private String status;

    @Nullable
    private Integer responseCode;

    @Valid
    @Nullable
    private OffsetDateTime createdAt;
}

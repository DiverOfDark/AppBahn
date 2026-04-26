package eu.appbahn.platform.resource.controller;

import eu.appbahn.platform.api.webhook.WebhookTriggerResponse;
import eu.appbahn.platform.api.webhook.WebhooksApi;
import eu.appbahn.platform.common.exception.NotImplementedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WebhooksController implements WebhooksApi {

    @Override
    public ResponseEntity<WebhookTriggerResponse> triggerWebhook(String resourceSlug) {
        throw new NotImplementedException();
    }
}

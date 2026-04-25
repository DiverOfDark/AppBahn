package eu.appbahn.platform.resource.controller;

import eu.appbahn.platform.api.webhook.WebhookTriggerResponse;
import eu.appbahn.platform.api.webhook.WebhooksApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class WebhooksController implements WebhooksApi {

    @Override
    public ResponseEntity<WebhookTriggerResponse> triggerWebhook(String resourceSlug) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}

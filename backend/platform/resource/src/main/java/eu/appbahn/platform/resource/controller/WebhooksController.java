package eu.appbahn.platform.resource.controller;

import eu.appbahn.platform.api.webhook.WebhooksApi;
import eu.appbahn.platform.common.idempotency.IdempotencyOptOut;
import eu.appbahn.platform.resource.service.WebhookReceiverService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public webhook receiver. The path token is the entire authentication signal — no headers,
 * no body. Always responds {@code 202 Accepted} on a token hit (deploy decision happens async
 * on the operator side); unknown tokens get {@code 404} via {@code NotFoundException} so the
 * endpoint isn't a token-enumeration oracle.
 */
@RestController
@RequestMapping("/api/v1")
public class WebhooksController implements WebhooksApi {

    private final WebhookReceiverService receiver;

    public WebhooksController(WebhookReceiverService receiver) {
        this.receiver = receiver;
    }

    @Override
    @IdempotencyOptOut
    public ResponseEntity<Void> receiveWebhook(String token) {
        receiver.receive(token);
        return ResponseEntity.accepted().build();
    }
}

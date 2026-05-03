package eu.appbahn.platform.api.webhook;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@Tag(name = "Webhooks")
public interface WebhooksApi {
    /**
     * POST /webhooks/{token} : ReceiveWebhook
     *
     * <p>Provider-agnostic webhook receiver. The path token is the entire authentication
     * signal — no headers checked, no body parsed, no provider-specific shape. On a token hit
     * the platform enqueues a {@code nudge-image-source} tunnel command for the cluster
     * owning the bound ImageSource; the operator stamps {@code status.lastWebhookAt} and the
     * reconciler re-pulls HEAD itself. Always returns {@code 202 Accepted} with empty body on
     * success; unknown tokens get {@code 404 Not Found} (same response as a non-existent
     * ImageSource, so the endpoint isn't a token-enumeration oracle).
     *
     * @param token per-ImageSource opaque capability token
     */
    @RequestMapping(method = RequestMethod.POST, value = "/webhooks/{token}")
    ResponseEntity<Void> receiveWebhook(@PathVariable("token") String token);
}

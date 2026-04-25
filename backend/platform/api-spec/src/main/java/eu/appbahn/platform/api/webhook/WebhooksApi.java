package eu.appbahn.platform.api.webhook;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@Tag(name = "Webhooks")
public interface WebhooksApi {
    /**
     * POST /webhooks/{resource_slug} : TriggerWebhook
     *
     * @param resourceSlug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Not found (status code 404)
     *         or Rate limit exceeded (status code 429)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/webhooks/{resource_slug}",
            produces = {"application/json"})
    ResponseEntity<WebhookTriggerResponse> triggerWebhook(@PathVariable("resource_slug") String resourceSlug);
}

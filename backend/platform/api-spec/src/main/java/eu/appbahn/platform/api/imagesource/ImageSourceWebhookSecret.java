package eu.appbahn.platform.api.imagesource;

import lombok.Data;

/**
 * One-time response body returned when a webhook token is minted/rotated. Carries the
 * plaintext token alongside the trigger URL — the client is expected to surface this to the
 * user once and not persist it on the server.
 */
@Data
public class ImageSourceWebhookSecret {

    /** Plaintext capability token. Returned ONCE; not readable from any subsequent endpoint. */
    private String secret;

    /** Full URL providers should POST to, e.g. {@code https://platform/api/v1/webhooks/<token>}. */
    private String url;

    public ImageSourceWebhookSecret() {}

    public ImageSourceWebhookSecret(String secret, String url) {
        this.secret = secret;
        this.url = url;
    }
}

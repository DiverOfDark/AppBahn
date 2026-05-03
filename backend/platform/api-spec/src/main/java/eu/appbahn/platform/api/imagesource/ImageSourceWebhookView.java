package eu.appbahn.platform.api.imagesource;

import lombok.Data;

/** Read-only projection of the webhook config: trigger URL + masked secret. */
@Data
public class ImageSourceWebhookView {

    /** Full URL providers should POST to, e.g. {@code https://platform/api/v1/webhooks/<token>}. */
    private String url;

    /** Masked secret form, e.g. {@code wh_••••••••abcd}. The real token is only returned at rotate time. */
    private String maskedSecret;

    public ImageSourceWebhookView() {}

    public ImageSourceWebhookView(String url, String maskedSecret) {
        this.url = url;
        this.maskedSecret = maskedSecret;
    }
}

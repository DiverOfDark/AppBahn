package eu.appbahn.platform.api.tunnel;

import lombok.Data;

/**
 * Tells the operator that a webhook arrived for a {@code type: git} ImageSource so it should
 * patch {@code status.lastWebhookAt} and trigger a fresh reconcile. The operator pulls HEAD
 * itself on the next reconcile — there is no payload to consume.
 */
@Data
public class NudgeImageSource {

    /** SSE {@code event:} name that carries this payload. */
    public static final String EVENT_NAME = "nudge-image-source";

    private String correlationId;
    private String namespace;
    private String name;
}

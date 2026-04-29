package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Polling cadence for {@code type: git}. {@code intervalSecondsAfterWebhook} and
 * {@code webhookFreshnessSeconds} stay inert in this PR — webhook delivery lands in #179.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageSourcePoll {
    private Integer intervalSeconds;
    private Integer intervalSecondsAfterWebhook;
    private Integer webhookFreshnessSeconds;
}

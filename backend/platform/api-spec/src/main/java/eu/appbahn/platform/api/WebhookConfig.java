package eu.appbahn.platform.api;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class WebhookConfig {

    @Nullable
    private String url;

    @Nullable
    private String secretMasked;
}

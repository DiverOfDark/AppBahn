package eu.appbahn.platform.api.resource;

import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Non-persistent preview of what {@link CreateResourceRequest} would mint: the slug the
 * platform would allocate and the primary ingress domain that would be assigned. Returned
 * by {@code POST /resources/preview}. Because slug generation embeds a random suffix, each
 * call returns a different result and the SPA should treat the values as illustrative.
 */
@Data
public class ResourcePreviewResponse {

    @Nullable
    private String slug;

    /**
     * Primary ingress domain for the lowest-numbered ingress port: the user-supplied value
     * if set, otherwise the platform-minted {@code {slug}.{baseDomain}}. {@code null} when
     * the request has no ingress ports.
     */
    @Nullable
    private String domain;
}

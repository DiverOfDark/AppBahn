package eu.appbahn.platform.api.tunnel;

import lombok.Data;

/**
 * Create-or-update a namespace owned by AppBahn. Operator applies the labels
 * ({@code managed-by} + {@code environment-slug}) via server-side apply, so re-sending the
 * same command is a no-op once the namespace exists.
 */
@Data
public class ApplyNamespace {

    /** SSE {@code event:} name that carries this payload. */
    public static final String EVENT_NAME = "apply-namespace";

    private String correlationId;
    private String namespace;
    private String environmentSlug;
}

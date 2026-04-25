package eu.appbahn.platform.api.tunnel;

import lombok.Data;

@Data
public class DeleteResource {

    /** SSE {@code event:} name that carries this payload. */
    public static final String EVENT_NAME = "delete-resource";

    private String correlationId;
    private String namespace;
    private String resourceSlug;
}

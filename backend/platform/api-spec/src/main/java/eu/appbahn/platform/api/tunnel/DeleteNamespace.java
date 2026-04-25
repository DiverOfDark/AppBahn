package eu.appbahn.platform.api.tunnel;

import lombok.Data;

/**
 * Delete an AppBahn-owned namespace. Operator's K8s call returns immediately; actual
 * removal is async (K8s drains contained resources first).
 */
@Data
public class DeleteNamespace {

    /** SSE {@code event:} name that carries this payload. */
    public static final String EVENT_NAME = "delete-namespace";

    private String correlationId;
    private String namespace;
}

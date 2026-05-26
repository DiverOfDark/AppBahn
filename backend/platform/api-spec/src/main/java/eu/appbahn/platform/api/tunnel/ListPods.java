package eu.appbahn.platform.api.tunnel;

import lombok.Data;

/**
 * Platform→operator query: list pods backing a Resource and report per-pod CPU/memory.
 * The operator filters the namespace by the {@code appbahn.eu/resource} label so the
 * result is scoped to one Resource. The reply lands on the ack endpoint as a
 * {@link ListPodsResult} payload.
 */
@Data
public class ListPods {

    public static final String EVENT_NAME = "list-pods";

    private String correlationId;

    private String namespace;

    /** Resource slug; matched against the {@code appbahn.eu/resource} pod label. */
    private String resourceSlug;
}

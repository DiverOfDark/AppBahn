package eu.appbahn.platform.api.tunnel;

import eu.appbahn.shared.crd.ResourceCrd;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Platform→operator command: apply (create-or-update) a Resource CRD to this cluster.
 * {@code resource} carries the full fabric8 {@link ResourceCrd}; the spec-internal IDs and
 * {@code stopped} toggle live inside the spec, not on the envelope.
 */
@Data
public class ApplyResource {

    /** SSE {@code event:} name that carries this payload. */
    public static final String EVENT_NAME = "apply-resource";

    private String correlationId;

    /** K8s namespace the CR lives in — derived from environment_slug + namespace_prefix. */
    private String namespace;

    @Valid
    private ResourceCrd resource;

    /** Optional optimistic-concurrency hint; empty string when not set. */
    @Nullable
    private String expectedResourceVersion;
}

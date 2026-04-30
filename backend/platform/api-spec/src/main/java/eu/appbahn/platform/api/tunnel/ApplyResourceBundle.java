package eu.appbahn.platform.api.tunnel;

import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import jakarta.validation.Valid;
import lombok.Data;

/**
 * Atomic create-or-update of a Resource + its sibling ImageSource. The operator applies the
 * Resource first (server-side apply, idempotent), reads the resulting UID, sets the ImageSource
 * {@code metadata.ownerReferences[0]} to that UID, then applies the ImageSource. Same command
 * for first-create and ongoing edits; SSA makes unchanged fields a no-op.
 */
@Data
public class ApplyResourceBundle {

    /** SSE {@code event:} name that carries this payload. */
    public static final String EVENT_NAME = "apply-resource-bundle";

    private String correlationId;

    /** K8s namespace both CRs live in — derived from environment_slug + namespace_prefix. */
    private String namespace;

    @Valid
    private ResourceCrd resource;

    @Valid
    private ImageSourceCrd imageSource;
}

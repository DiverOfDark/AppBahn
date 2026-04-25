package eu.appbahn.platform.api.tunnel;

import eu.appbahn.shared.crd.ResourceCrd;
import jakarta.validation.Valid;
import lombok.Data;

/**
 * Operator-emitted snapshot of one resource. {@code resource} carries the full CRD
 * (metadata + spec + status); {@code generation} / {@code resourceVersion} ride alongside
 * as explicit envelope fields even though they also live inside {@code metadata} — the
 * platform uses them for staleness checks without deserialising.
 */
@Data
public class ResourceSyncItem {

    @Valid
    private ResourceCrd resource;

    private long generation;
    private String resourceVersion;
}

package eu.appbahn.platform.api.tunnel;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Emitted by the operator's admission webhook after an allow decision so the platform can
 * seed {@code resource_cache} with PENDING before the reconciler's watch fires — fast
 * read-after-write for {@code kubectl apply}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdmissionApproved extends OperatorEvent {

    @Valid
    private ResourceSyncItem item;

    public AdmissionApproved() {
        setType("admission-approved");
    }
}

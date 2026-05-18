package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.generator.annotation.Min;
import lombok.Data;

/**
 * Pod-disruption budget knob exposed on {@link ResourceConfig.HostingConfig}. When
 * {@link #minAvailable} is non-null, the operator reconciles a {@code policy/v1 PodDisruptionBudget}
 * alongside the Deployment. Leaving it null (the default) deletes any existing PDB the operator
 * had created previously — no budget guarantee.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PdbConfig {

    @Min(0)
    private Integer minAvailable;
}

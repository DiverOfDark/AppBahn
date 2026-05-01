package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Data;

/**
 * Optional override for the container's entrypoint/args. The image's own ENTRYPOINT/CMD is
 * authoritative for the run command; this block exists for the rare case where the user wants
 * to run something other than the image's default. Maps directly to a K8s
 * {@code pod.spec.containers[0].command} / {@code .args} pair: when {@code command} is
 * non-empty it overrides the image's ENTRYPOINT; when {@code args} is non-empty it overrides
 * the image's CMD. Either field may be null/empty independently.
 *
 * <p>At least one of {@code command} or {@code args} must be non-empty when the override block
 * is present — an empty override is meaningless and validation rejects it.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommandOverride {
    /** Maps to K8s {@code container.command}. Null/empty = use image's ENTRYPOINT. */
    private List<String> command;

    /** Maps to K8s {@code container.args}. Null/empty = use image's CMD. */
    private List<String> args;
}

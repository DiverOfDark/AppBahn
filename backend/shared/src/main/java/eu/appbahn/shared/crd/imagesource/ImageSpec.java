package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Manually pinned image reference. The image's own ENTRYPOINT/CMD is authoritative for the run
 * command — if a Resource needs to override, it sets {@code spec.commandOverride}.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageSpec {
    private String ref;
}

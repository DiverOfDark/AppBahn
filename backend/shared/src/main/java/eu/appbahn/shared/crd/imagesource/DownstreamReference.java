package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Coordinates of a downstream ImageSource pointing at this upstream. Encoded as a JSON array of
 * these objects in the {@code appbahn.eu/downstream-references} annotation on an upstream
 * ImageSource. The platform's promotion broker maintains the annotation; the operator's
 * deletion-protection cleanup reads it to block hard-deletes while cross-cluster downstreams
 * still exist.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DownstreamReference {
    private String cluster;
    private String namespace;
    private String name;
}

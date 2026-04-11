package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Defines how to map a target CRD's status to AppBahn resource status.
 * Each field contains a JSONPath expression evaluated against the target CRD's status.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusMappingDefinition {

    /** JSONPath expression that evaluates to true when the resource is ready. */
    private String ready;

    /** JSONPath expression that evaluates to true when the resource is in error. */
    private String error;

    /** JSONPath expression to extract a human-readable status message. */
    private String message;
}

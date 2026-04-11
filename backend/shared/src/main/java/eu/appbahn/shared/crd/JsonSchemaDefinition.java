package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.crd.generator.annotation.PreserveUnknownFields;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Typed representation of a JSON Schema object used for configSchema and adminConfigSchema
 * in ResourceTypeDefinition CRDs.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@PreserveUnknownFields
public class JsonSchemaDefinition {

    private String type;
    private String title;
    private String description;
    private List<String> required;
    private Map<String, JsonSchemaProperty> properties;
    private JsonSchemaProperty additionalProperties;
}

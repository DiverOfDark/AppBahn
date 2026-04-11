package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.crd.generator.annotation.PreserveUnknownFields;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Typed representation of a single JSON Schema property.
 * Top-level fields are strongly typed; nested property schemas are captured as maps
 * to avoid cyclic references in the CRD generator.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@PreserveUnknownFields
public class JsonSchemaProperty {

    private String type;
    private String title;
    private String description;
    private Object defaultValue;
    private List<String> enumValues;
    private String pattern;
    private Integer minimum;
    private Integer maximum;

    @PreserveUnknownFields
    private Map<String, Map<String, Object>> properties;

    @PreserveUnknownFields
    private Map<String, Object> additionalProperties;

    @PreserveUnknownFields
    private Map<String, Object> items;

    private List<String> required;

    @com.fasterxml.jackson.annotation.JsonSetter("default")
    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    @com.fasterxml.jackson.annotation.JsonGetter("default")
    public Object getDefaultValue() {
        return defaultValue;
    }

    @com.fasterxml.jackson.annotation.JsonSetter("enum")
    public void setEnumValues(List<String> enumValues) {
        this.enumValues = enumValues;
    }

    @com.fasterxml.jackson.annotation.JsonGetter("enum")
    public List<String> getEnumValues() {
        return enumValues;
    }
}

package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.crd.generator.annotation.PreserveUnknownFields;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceTypeDefinitionSpec {

    private String displayName;
    private String description;
    private String category;
    private String icon;

    @PreserveUnknownFields
    private JsonSchemaDefinition configSchema;

    private List<SecretOutput> secretOutput;
    private List<ConnectionDisplayEntry> connectionDisplay;
    private Discovery discovery;
    private Lifecycle lifecycle;

    @PreserveUnknownFields
    private Map<String, String> quotaDimensions;

    @PreserveUnknownFields
    private JsonSchemaDefinition adminConfigSchema;

    private Reconciliation reconciliation;

    @Data
    public static class SecretOutput {
        private String name;
        private String secretNamePattern;
        private List<SecretKey> keys;
    }

    @Data
    public static class SecretKey {
        private String key;
        private String description;
    }

    @Data
    public static class ConnectionDisplayEntry {
        private String label;
        private ValueFrom valueFrom;
        private boolean masked;
    }

    @Data
    public static class ValueFrom {
        private String secret;
        private String secretKey;
    }

    @Data
    public static class Discovery {
        private String crd;
    }

    @Data
    public static class Lifecycle {
        private boolean stoppable;
        private String stopBehavior;
    }

    @Data
    public static class Reconciliation {
        private CrdRef crd;

        // JsonNode is intentional here: specTemplate maps to arbitrary third-party CRD specs
        // via Go-template expressions. The structure is inherently schema-free (any nesting depth,
        // any field names) so a typed POJO is not feasible.
        @PreserveUnknownFields
        private JsonNode specTemplate;

        @PreserveUnknownFields
        private StatusMappingDefinition statusMapping;
    }

    @Data
    public static class CrdRef {
        private String apiVersion;
        private String kind;
    }
}

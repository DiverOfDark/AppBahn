package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceTypeDefinitionSpec {

    private String displayName;
    private String description;
    private String icon;
    private JsonNode configSchema;
    private List<SecretOutput> secretOutput;
    private List<ConnectionDisplayEntry> connectionDisplay;
    private Discovery discovery;
    private Lifecycle lifecycle;
    private JsonNode quotaDimensions;
    private JsonNode adminConfigSchema;
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
        private JsonNode specTemplate;
        private JsonNode statusMapping;
    }

    @Data
    public static class CrdRef {
        private String apiVersion;
        private String kind;
    }
}

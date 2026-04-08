package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceSpec {

    private String type;
    private String name;
    private JsonNode config;
    private List<ResourceLink> links;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResourceLink {
        private String resource;
        private String secret;
        private Map<String, String> env;
    }
}

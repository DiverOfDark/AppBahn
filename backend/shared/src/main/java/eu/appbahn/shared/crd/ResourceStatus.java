package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceStatus {

    private String phase;
    private String message;
    private Long observedGeneration;
    private PodStatus pods;
    private List<String> domains;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PodStatus {
        private int ready;
        private int total;
    }
}

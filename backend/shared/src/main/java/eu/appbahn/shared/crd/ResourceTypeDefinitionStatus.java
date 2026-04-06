package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceTypeDefinitionStatus {

    private boolean available;
    private String message;
}

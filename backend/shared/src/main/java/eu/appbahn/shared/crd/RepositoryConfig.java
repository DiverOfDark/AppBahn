package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import eu.appbahn.shared.model.AuthType;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RepositoryConfig {
    private String url;
    private String branch;
    private AuthType authType;
    private String secretName;
}

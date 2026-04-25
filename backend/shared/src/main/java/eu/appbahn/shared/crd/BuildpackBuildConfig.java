package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class BuildpackBuildConfig implements BuildConfig {

    private String type;
    private String builder;
    private Map<String, String> buildVars;
}

package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.crd.generator.annotation.PreserveUnknownFields;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class GitSource implements Source {

    private String type;
    private String pollInterval;
    private Boolean webhookEnabled;
    private String url;
    private String branch;
    private String path;
    private SourceAuth auth;

    @PreserveUnknownFields
    private BuildConfig buildConfig;
}

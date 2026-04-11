package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.crd.generator.annotation.PreserveUnknownFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitSource extends Source {

    private String url;
    private String branch;
    private String path;
    private SourceAuth auth;

    @PreserveUnknownFields
    private BuildConfig buildConfig;
}

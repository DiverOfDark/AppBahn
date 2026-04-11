package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PeelboxBuildConfig.class, name = "peelbox"),
    @JsonSubTypes.Type(value = BuildpackBuildConfig.class, name = "buildpack"),
    @JsonSubTypes.Type(value = RailpackBuildConfig.class, name = "railpack"),
    @JsonSubTypes.Type(value = DockerfileBuildConfig.class, name = "dockerfile")
})
public abstract class BuildConfig {

    private String type;
}

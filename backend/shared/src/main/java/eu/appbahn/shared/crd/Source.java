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
    @JsonSubTypes.Type(value = DockerSource.class, name = "docker"),
    @JsonSubTypes.Type(value = GitSource.class, name = "git"),
    @JsonSubTypes.Type(value = PromotionSource.class, name = "promotion")
})
public abstract class Source {

    private String type;
    private String pollInterval;
    private Boolean webhookEnabled;
}

package eu.appbahn.platform.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuditTargetType", enumAsRef = true)
public enum AuditTargetType {
    @JsonProperty("Workspace")
    WORKSPACE,

    @JsonProperty("Project")
    PROJECT,

    @JsonProperty("Environment")
    ENVIRONMENT,

    @JsonProperty("Resource")
    RESOURCE,

    @JsonProperty("Deployment")
    DEPLOYMENT
}

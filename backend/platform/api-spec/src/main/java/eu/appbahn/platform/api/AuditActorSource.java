package eu.appbahn.platform.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuditActorSource", enumAsRef = true)
public enum AuditActorSource {
    @JsonProperty("Api")
    API,

    @JsonProperty("Token")
    TOKEN,

    @JsonProperty("Kubectl")
    KUBECTL,

    @JsonProperty("System")
    SYSTEM
}

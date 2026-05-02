package eu.appbahn.platform.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuditDecision", enumAsRef = true)
public enum AuditDecision {
    @JsonProperty("Allowed")
    ALLOWED,

    @JsonProperty("Denied")
    DENIED
}

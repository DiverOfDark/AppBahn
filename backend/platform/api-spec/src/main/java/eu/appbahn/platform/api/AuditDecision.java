package eu.appbahn.platform.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuditDecision", enumAsRef = true)
public enum AuditDecision {
    ALLOWED,
    DENIED
}

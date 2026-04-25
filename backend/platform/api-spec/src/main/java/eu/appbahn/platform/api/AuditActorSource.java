package eu.appbahn.platform.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuditActorSource", enumAsRef = true)
public enum AuditActorSource {
    API,
    TOKEN,
    KUBECTL,
    SYSTEM
}

package eu.appbahn.platform.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuditTargetType", enumAsRef = true)
public enum AuditTargetType {
    WORKSPACE,
    PROJECT,
    ENVIRONMENT,
    RESOURCE,
    DEPLOYMENT
}

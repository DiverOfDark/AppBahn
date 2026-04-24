package eu.appbahn.tunnel.wire;

import eu.appbahn.platform.api.model.AuditAction;
import eu.appbahn.platform.api.model.AuditActorSource;
import eu.appbahn.platform.api.model.AuditDecision;
import eu.appbahn.platform.api.model.AuditTargetType;

/**
 * Bridges the OpenAPI-generated audit enums (used throughout the Spring platform)
 * with their proto-generated counterparts (used on the operator tunnel wire).
 *
 * The two enum families are kept in lock-step by {@code AuditEnumMapperTest};
 * adding a value on one side without the other fails the build.
 */
public final class AuditEnumMapper {

    private AuditEnumMapper() {}

    public static eu.appbahn.tunnel.v1.AuditAction toProto(AuditAction api) {
        return eu.appbahn.tunnel.v1.AuditAction.valueOf("AUDIT_ACTION_" + api.name());
    }

    public static AuditAction fromProto(eu.appbahn.tunnel.v1.AuditAction proto) {
        if (proto == eu.appbahn.tunnel.v1.AuditAction.AUDIT_ACTION_UNSPECIFIED
                || proto == eu.appbahn.tunnel.v1.AuditAction.UNRECOGNIZED) {
            throw new IllegalArgumentException("Unmappable proto AuditAction: " + proto);
        }
        return AuditAction.valueOf(proto.name().substring("AUDIT_ACTION_".length()));
    }

    public static eu.appbahn.tunnel.v1.AuditTargetType toProto(AuditTargetType api) {
        return eu.appbahn.tunnel.v1.AuditTargetType.valueOf("AUDIT_TARGET_TYPE_" + api.name());
    }

    public static AuditTargetType fromProto(eu.appbahn.tunnel.v1.AuditTargetType proto) {
        if (proto == eu.appbahn.tunnel.v1.AuditTargetType.AUDIT_TARGET_TYPE_UNSPECIFIED
                || proto == eu.appbahn.tunnel.v1.AuditTargetType.UNRECOGNIZED) {
            throw new IllegalArgumentException("Unmappable proto AuditTargetType: " + proto);
        }
        return AuditTargetType.valueOf(proto.name().substring("AUDIT_TARGET_TYPE_".length()));
    }

    public static eu.appbahn.tunnel.v1.AuditActorSource toProto(AuditActorSource api) {
        return eu.appbahn.tunnel.v1.AuditActorSource.valueOf("AUDIT_ACTOR_SOURCE_" + api.name());
    }

    public static AuditActorSource fromProto(eu.appbahn.tunnel.v1.AuditActorSource proto) {
        if (proto == eu.appbahn.tunnel.v1.AuditActorSource.AUDIT_ACTOR_SOURCE_UNSPECIFIED
                || proto == eu.appbahn.tunnel.v1.AuditActorSource.UNRECOGNIZED) {
            throw new IllegalArgumentException("Unmappable proto AuditActorSource: " + proto);
        }
        return AuditActorSource.valueOf(proto.name().substring("AUDIT_ACTOR_SOURCE_".length()));
    }

    public static eu.appbahn.tunnel.v1.AuditDecision toProto(AuditDecision api) {
        return eu.appbahn.tunnel.v1.AuditDecision.valueOf("AUDIT_DECISION_" + api.name());
    }

    public static AuditDecision fromProto(eu.appbahn.tunnel.v1.AuditDecision proto) {
        if (proto == eu.appbahn.tunnel.v1.AuditDecision.AUDIT_DECISION_UNSPECIFIED
                || proto == eu.appbahn.tunnel.v1.AuditDecision.UNRECOGNIZED) {
            throw new IllegalArgumentException("Unmappable proto AuditDecision: " + proto);
        }
        return AuditDecision.valueOf(proto.name().substring("AUDIT_DECISION_".length()));
    }
}

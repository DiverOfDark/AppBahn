package eu.appbahn.platform.api.tunnel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Polymorphic base for operator→platform events. Subtypes are selected on the wire by the
 * {@code type} property. Mirrors the original proto {@code oneof event} with one variant
 * per case — a PushEvents request carries a list of these, potentially mixing variants
 * (e.g. a sync batch + deletion in the same call).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ResourceSyncBatch.class, name = "resource-sync-batch"),
    @JsonSubTypes.Type(value = ResourceDeletedBatch.class, name = "resource-deleted-batch"),
    @JsonSubTypes.Type(value = FullResourceSyncChunk.class, name = "full-resource-sync-chunk"),
    @JsonSubTypes.Type(value = ResourceTypeSyncChunk.class, name = "resource-type-sync-chunk"),
    @JsonSubTypes.Type(value = DeploymentStatusUpdate.class, name = "deployment-status-update"),
    @JsonSubTypes.Type(value = AdmissionCacheMissReport.class, name = "admission-cache-miss-report"),
    @JsonSubTypes.Type(value = AdmissionApproved.class, name = "admission-approved"),
    @JsonSubTypes.Type(value = AuditLogEvent.class, name = "audit-log")
})
@Schema(
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "resource-sync-batch", schema = ResourceSyncBatch.class),
            @DiscriminatorMapping(value = "resource-deleted-batch", schema = ResourceDeletedBatch.class),
            @DiscriminatorMapping(value = "full-resource-sync-chunk", schema = FullResourceSyncChunk.class),
            @DiscriminatorMapping(value = "resource-type-sync-chunk", schema = ResourceTypeSyncChunk.class),
            @DiscriminatorMapping(value = "deployment-status-update", schema = DeploymentStatusUpdate.class),
            @DiscriminatorMapping(value = "admission-cache-miss-report", schema = AdmissionCacheMissReport.class),
            @DiscriminatorMapping(value = "admission-approved", schema = AdmissionApproved.class),
            @DiscriminatorMapping(value = "audit-log", schema = AuditLogEvent.class)
        },
        subTypes = {
            ResourceSyncBatch.class,
            ResourceDeletedBatch.class,
            FullResourceSyncChunk.class,
            ResourceTypeSyncChunk.class,
            DeploymentStatusUpdate.class,
            AdmissionCacheMissReport.class,
            AdmissionApproved.class,
            AuditLogEvent.class
        })
public abstract class OperatorEvent {

    protected String type;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}

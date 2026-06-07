package eu.appbahn.platform.api.tunnel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Polymorphic base for command-response bodies — the data half of an ack for
 * read-style commands (e.g. {@code list-pods}, {@code cluster-capacity}). Action commands
 * (apply/delete) leave this null and use only {@link CommandResponse#getStatus()} +
 * {@link CommandResponse#getMessage()}. Subtypes are selected on the wire by the
 * {@code type} discriminator, mirroring the {@link OperatorEvent} pattern.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ListPodsResult.class, name = "list-pods-result"),
    @JsonSubTypes.Type(value = ClusterCapacityResult.class, name = "cluster-capacity-result"),
    @JsonSubTypes.Type(value = MetricsResult.class, name = "metrics-result"),
    @JsonSubTypes.Type(value = LogsResult.class, name = "logs-result")
})
@Schema(
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "list-pods-result", schema = ListPodsResult.class),
            @DiscriminatorMapping(value = "cluster-capacity-result", schema = ClusterCapacityResult.class),
            @DiscriminatorMapping(value = "metrics-result", schema = MetricsResult.class),
            @DiscriminatorMapping(value = "logs-result", schema = LogsResult.class)
        },
        subTypes = {ListPodsResult.class, ClusterCapacityResult.class, MetricsResult.class, LogsResult.class})
public abstract class CommandResponsePayload {

    protected String type;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}

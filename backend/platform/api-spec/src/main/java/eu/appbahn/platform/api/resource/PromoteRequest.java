package eu.appbahn.platform.api.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Body for {@code POST /resources/{slug}/promote}. {@code digest} is optional — when omitted the
 * server promotes whatever the bound ImageSource's upstream is currently exposing as
 * {@code latestArtifact}. When set, the server pins to the supplied digest (e.g. promoting from
 * a deployment audit row).
 */
@Data
@Schema(name = "PromoteRequest", description = "Request body for resource promote operation")
public class PromoteRequest {

    @Schema(description = "Specific digest to promote to. When null, uses upstream's current latestArtifact.")
    private String digest;
}

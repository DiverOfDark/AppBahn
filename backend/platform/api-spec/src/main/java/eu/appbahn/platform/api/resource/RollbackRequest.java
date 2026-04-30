package eu.appbahn.platform.api.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Data;

/**
 * Body for {@code POST /resources/{slug}/rollback}. {@code deploymentId} is optional — when null
 * the server rolls back to the most recent successful deployment that is not the current one.
 * When set, the deployment id is looked up, its imageRef digest read, and the bound
 * ImageSource's {@code pinnedDigest} set to that value.
 *
 * <p>Rollback is supported on {@code type: image} and {@code type: imageSource} ImageSources;
 * for {@code type: git}, callers should revert the source commit instead — the API surfaces a
 * 422 with a clear message.
 */
@Data
@Schema(name = "RollbackRequest", description = "Request body for resource rollback operation")
public class RollbackRequest {

    @Schema(
            description =
                    "Specific deployment audit id to roll back to. When null, uses the previous successful deployment.")
    private UUID deploymentId;
}

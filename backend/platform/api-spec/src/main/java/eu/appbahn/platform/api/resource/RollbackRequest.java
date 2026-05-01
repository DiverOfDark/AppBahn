package eu.appbahn.platform.api.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Data;

/**
 * Body for {@code POST /resources/{slug}/rollback}. {@code deploymentId} is optional — when null
 * the server rolls back to the most recent successful deployment that is not the current one.
 * When set, the deployment id is looked up, its imageRef read, and {@code Resource.spec.pinnedRelease}
 * is set to a snapshot built from that audit row.
 *
 * <p>Rollback is supported on every ImageSource type ({@code git}, {@code image}, {@code imageSource}):
 * the pin lives on the Resource, not the ImageSource, so no rebuild runs. Vercel/Railway/Heroku
 * semantics — the user can re-pin to {@code v(N-k)} of any app instantly. Use
 * {@code POST /resources/{slug}/unpin} to clear the pin.
 */
@Data
@Schema(name = "RollbackRequest", description = "Request body for resource rollback operation")
public class RollbackRequest {

    @Schema(
            description =
                    "Specific deployment audit id to roll back to. When null, uses the previous successful deployment.")
    private UUID deploymentId;
}

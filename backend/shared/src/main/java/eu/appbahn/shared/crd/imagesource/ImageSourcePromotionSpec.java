package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Body of the {@code spec.imageSource} sub-block when {@link ImageSourceSpec#getType()} is
 * {@link ImageSourceType#IMAGE_SOURCE}. Either {@code autoPromote=true} (follow upstream's
 * latestArtifact automatically) or {@code pinnedDigest != null} (manual pin / rollback) must be
 * set — never both.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageSourcePromotionSpec {
    private ImageSourceUpstreamSpec upstream;
    private Boolean autoPromote;
    private String pinnedDigest;
}

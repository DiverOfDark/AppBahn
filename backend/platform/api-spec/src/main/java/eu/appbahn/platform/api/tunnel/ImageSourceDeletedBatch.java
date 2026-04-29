package eu.appbahn.platform.api.tunnel;

import eu.appbahn.shared.util.SlugFormat;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImageSourceDeletedBatch extends OperatorEvent {

    private List<@Pattern(regexp = SlugFormat.SLUG_REGEX, message = "must match slug format") String> imageSourceSlugs =
            new ArrayList<>();

    public ImageSourceDeletedBatch() {
        setType("image-source-deleted-batch");
    }
}

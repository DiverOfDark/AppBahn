package eu.appbahn.platform.api.resource;

import eu.appbahn.shared.crd.imagesource.ImageSourceSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CreateResourceRequest {

    @NotNull
    private String name;

    @NotNull
    private String type;

    @NotNull
    private String environmentSlug;

    @NotNull
    @Valid
    private eu.appbahn.shared.crd.ResourceConfig config;

    /**
     * The ImageSource paired with this Resource. {@code metadata.name} on the wire is ignored —
     * the platform mints a sibling ImageSource named after the Resource slug.
     */
    @NotNull
    @Valid
    private ImageSourceSpec imageSource;

    @Valid
    private List<eu.appbahn.shared.crd.ResourceSpec.LinkConfig> links = new ArrayList<>();
}

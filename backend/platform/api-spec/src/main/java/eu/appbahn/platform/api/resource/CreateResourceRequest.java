package eu.appbahn.platform.api.resource;

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

    @Valid
    private List<eu.appbahn.shared.crd.ResourceSpec.LinkConfig> links = new ArrayList<>();
}

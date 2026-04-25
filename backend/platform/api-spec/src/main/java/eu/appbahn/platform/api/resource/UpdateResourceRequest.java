package eu.appbahn.platform.api.resource;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class UpdateResourceRequest {

    @Nullable
    private String name;

    @Valid
    @Nullable
    private eu.appbahn.shared.crd.ResourceConfig config;

    @Valid
    private List<eu.appbahn.shared.crd.ResourceSpec.LinkConfig> links = new ArrayList<>();
}

package eu.appbahn.platform.api.resource;

import eu.appbahn.platform.api.Deployment;
import eu.appbahn.platform.api.PagedResponse;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PagedDeploymentResponse extends PagedResponse {

    @Valid
    private List<Deployment> content = new ArrayList<>();
}

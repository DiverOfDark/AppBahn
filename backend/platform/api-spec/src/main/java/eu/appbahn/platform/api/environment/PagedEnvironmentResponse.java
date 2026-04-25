package eu.appbahn.platform.api.environment;

import eu.appbahn.platform.api.Environment;
import eu.appbahn.platform.api.PagedResponse;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PagedEnvironmentResponse extends PagedResponse {

    @Valid
    private List<Environment> content = new ArrayList<>();
}

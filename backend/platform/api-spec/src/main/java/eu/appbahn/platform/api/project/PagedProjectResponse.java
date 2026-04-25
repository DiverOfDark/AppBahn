package eu.appbahn.platform.api.project;

import eu.appbahn.platform.api.PagedResponse;
import eu.appbahn.platform.api.Project;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PagedProjectResponse extends PagedResponse {

    @Valid
    private List<Project> content = new ArrayList<>();
}

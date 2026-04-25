package eu.appbahn.platform.api.workspace;

import eu.appbahn.platform.api.PagedResponse;
import eu.appbahn.platform.api.Workspace;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PagedWorkspaceResponse extends PagedResponse {

    @Valid
    private List<Workspace> content = new ArrayList<>();
}

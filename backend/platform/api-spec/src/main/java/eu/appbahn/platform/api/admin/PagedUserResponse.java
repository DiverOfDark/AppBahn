package eu.appbahn.platform.api.admin;

import eu.appbahn.platform.api.PagedResponse;
import eu.appbahn.platform.api.User;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PagedUserResponse extends PagedResponse {

    @Valid
    private List<User> content = new ArrayList<>();
}

package eu.appbahn.platform.api.admin;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class UpdateClusterRequest {

    @Nullable
    private String description;
}

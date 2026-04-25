package eu.appbahn.platform.api.admin;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class UpdateResourceTypeAdminConfigRequest {

    @Nullable
    private String storageClass;

    private Map<String, String> labels = new HashMap<>();
}

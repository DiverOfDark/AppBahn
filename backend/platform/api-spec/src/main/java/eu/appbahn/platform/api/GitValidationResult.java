package eu.appbahn.platform.api;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class GitValidationResult {

    @Nullable
    private Boolean valid;

    @Nullable
    private String message;

    private List<String> branches = new ArrayList<>();
}

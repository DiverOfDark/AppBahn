package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class BuildDetectionJob {

    @Valid
    @Nullable
    private UUID jobId;

    @Nullable
    private String status;

    @Nullable
    private String step;

    @Nullable
    private String message;

    private List<Object> universalBuild = new ArrayList<>();

    @Nullable
    private String error;
}

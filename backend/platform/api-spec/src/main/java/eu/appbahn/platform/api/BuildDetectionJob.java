package eu.appbahn.platform.api;

import eu.appbahn.shared.crd.imagesource.PeelboxBuildOptions;
import jakarta.validation.Valid;
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

    @Nullable
    private PeelboxBuildOptions universalBuild;

    @Nullable
    private String error;
}

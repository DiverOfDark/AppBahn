package eu.appbahn.platform.api.git;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class BuildDetectionJobCreated {

    @Valid
    @Nullable
    private UUID jobId;
}

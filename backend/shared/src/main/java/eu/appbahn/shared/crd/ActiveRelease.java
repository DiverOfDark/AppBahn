package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import lombok.Data;

/**
 * Snapshot of the artifact the operator currently has rolled out, mirrored from the bound
 * ImageSource's {@code status.latestArtifact} when the new {@code spec.release.fromImageSource}
 * path is in use. {@code sourceCommit} is null when the upstream artifact is not git-traceable
 * (e.g. {@code ImageSource.type=image}).
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActiveRelease {
    private String sourceCommit;
    private String imageRef;
    private List<String> runCommand;
    private Instant activatedAt;
}

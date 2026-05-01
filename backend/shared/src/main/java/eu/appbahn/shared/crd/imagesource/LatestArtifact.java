package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.Data;

/**
 * Latest successful artifact this ImageSource has produced or pinned. {@code sourceCommit}
 * is non-null only when the artifact is git-traceable (built from a commit). For
 * {@code type: image} this carries the pinned ref; for {@code type: git}, the build
 * pipeline populates it. The image's own ENTRYPOINT/CMD is authoritative for the run
 * command — if a Resource needs to override, it sets {@code spec.commandOverride}.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LatestArtifact {
    private String sourceCommit;
    private String imageRef;
    private Instant builtAt;
}

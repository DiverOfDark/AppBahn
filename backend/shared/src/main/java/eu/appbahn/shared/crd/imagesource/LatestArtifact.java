package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import lombok.Data;

/**
 * Latest successful artifact this ImageSource has produced or pinned. {@code sourceCommit}
 * is non-null only when the artifact is git-traceable (built from a commit). For
 * {@code type: image} this carries the pinned ref; for {@code type: git}, builds in a
 * later PR populate it.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LatestArtifact {
    private String sourceCommit;
    private String imageRef;
    private List<String> runCommand;
    private Instant builtAt;
}

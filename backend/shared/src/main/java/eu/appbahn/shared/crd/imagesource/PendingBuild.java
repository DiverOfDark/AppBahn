package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.Data;

/**
 * In-flight or queued build snapshot. The reconciler keeps at most one of each lifecycle in
 * {@link ImageSourceStatus}: while {@code BUILDING} is occupied, a new commit moves the
 * existing {@code QUEUED} entry to {@link BuildLifecycle#SUPERSEDED} (event emitted, then
 * cleared on the next reconcile) and replaces it with the new commit at {@code QUEUED}.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PendingBuild {
    private String sourceCommit;
    private BuildLifecycle lifecycle;
    private String deploymentId;
    private String jobName;
    private Instant startedAt;
    private String errorMessage;
}

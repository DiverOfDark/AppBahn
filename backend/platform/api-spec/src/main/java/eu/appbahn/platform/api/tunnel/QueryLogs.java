package eu.appbahn.platform.api.tunnel;

import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Platform→operator query: run a LogsQL query against the in-cluster Victoria Logs for the pods
 * backing a Resource. The operator scopes the query to {@code namespace} and the
 * {@code appbahn.eu/resource} label, applies the optional {@code container}/{@code pod}/
 * {@code sinceEpochSeconds} filters and the {@code limit}, and replies on the ack endpoint as a
 * {@link LogsResult} payload (one entry per matched log line).
 *
 * <p>When the operator has no Victoria Logs endpoint configured, it replies with
 * {@link LogsResult#getAvailable()} = {@code false} and an empty line list so the platform can
 * surface the graceful "not available" message instead of an error.
 */
@Data
public class QueryLogs {

    public static final String EVENT_NAME = "query-logs";

    private String correlationId;

    private String namespace;

    /** Resource slug; matched against the {@code appbahn.eu/resource} pod label. */
    private String resourceSlug;

    /** Optional pod-name filter; when set, narrows the query to a single pod. */
    @Nullable
    private String pod;

    /** Optional container-name filter; when set, narrows the query to a single container. */
    @Nullable
    private String container;

    /** Optional deployment id filter; matched against the deployment label on the pods. */
    @Nullable
    private String deploymentId;

    /** Lower time bound as a Unix epoch second; 0 means no lower bound. */
    private long sinceEpochSeconds;

    /** Maximum number of lines to return (already resolved by the platform). */
    private int limit;
}

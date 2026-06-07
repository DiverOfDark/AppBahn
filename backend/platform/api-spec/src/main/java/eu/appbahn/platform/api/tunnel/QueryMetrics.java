package eu.appbahn.platform.api.tunnel;

import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Platform→operator query: run a PromQL range query against the in-cluster Prometheus for
 * the pods backing a Resource. The operator scopes the query to {@code namespace} and the
 * {@code appbahn.eu/resource} label, builds the PromQL for {@link #kind}, and replies on the
 * ack endpoint as a {@link MetricsResult} payload (one series per pod).
 *
 * <p>When the operator has no Prometheus endpoint configured, it replies with
 * {@link MetricsResult#getAvailable()} = {@code false} and an empty series list so the
 * platform can surface the graceful "not available" message instead of an error.
 */
@Data
public class QueryMetrics {

    public static final String EVENT_NAME = "query-metrics";

    private String correlationId;

    private String namespace;

    /** Resource slug; matched against the {@code appbahn.eu/resource} pod label. */
    private String resourceSlug;

    private MetricKind kind;

    /** Range start as a Unix epoch second. */
    private long startEpochSeconds;

    /** Range end as a Unix epoch second. */
    private long endEpochSeconds;

    /** Query resolution in seconds (already resolved by the platform). */
    private int stepSeconds;

    /** Optional pod-name filter; when set, narrows the query to a single pod. */
    @Nullable
    private String pod;
}

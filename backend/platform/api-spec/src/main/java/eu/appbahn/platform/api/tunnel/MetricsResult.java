package eu.appbahn.platform.api.tunnel;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Per-pod time-series returned by the operator in response to {@link QueryMetrics}. When the
 * operator has no usable metrics provider, {@link #available} is {@code false}, {@link #series}
 * is empty, and {@link #message} carries the distinct reason (no provider configured vs. no
 * Prometheus URL) the console shows.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MetricsResult extends CommandResponsePayload {

    /** False when the operator has no usable metrics provider. */
    private boolean available;

    /** Distinct "not available" reason set by the operator when {@link #available} is false. */
    private String message;

    private List<MetricsResultSeries> series = new ArrayList<>();

    public MetricsResult() {
        setType("metrics-result");
    }
}

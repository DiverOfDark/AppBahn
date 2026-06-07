package eu.appbahn.platform.tunnel.events;

import eu.appbahn.platform.api.resource.MetricsResponse;
import eu.appbahn.platform.api.tunnel.MetricKind;
import eu.appbahn.platform.api.tunnel.MetricsResult;
import eu.appbahn.platform.api.tunnel.MetricsResultSample;
import eu.appbahn.platform.api.tunnel.MetricsResultSeries;
import eu.appbahn.platform.api.tunnel.QueryMetrics;
import eu.appbahn.platform.resource.service.MetricsService;
import eu.appbahn.platform.resource.service.MetricsSupplier;
import eu.appbahn.platform.tunnel.command.CommandResponseAwaiter;
import eu.appbahn.platform.tunnel.command.CommandTypes;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * Tunnel-backed {@link MetricsSupplier}: enqueues a {@link QueryMetrics} command, blocks for the
 * operator's ack, and maps the {@link MetricsResult} payload onto the public
 * {@link MetricsResponse} shape. When the operator reports no usable metrics provider
 * ({@code available=false}), the supplier surfaces the operator's distinct "not available" message.
 */
@Service
public class TunnelMetricsSupplier implements MetricsSupplier {

    /**
     * Range queries against Prometheus are heavier than a pod list — a ten-second budget covers
     * one slow PromQL round-trip without wedging the API thread for the full pending-command
     * sweeper window.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final CommandResponseAwaiter awaiter;

    public TunnelMetricsSupplier(CommandResponseAwaiter awaiter) {
        this.awaiter = awaiter;
    }

    @Override
    public MetricsResponse fetch(
            String clusterName,
            String namespace,
            String resourceSlug,
            MetricKind kind,
            long startEpochSeconds,
            long endEpochSeconds,
            int stepSeconds,
            String pod) {
        var command = new QueryMetrics();
        command.setNamespace(namespace);
        command.setResourceSlug(resourceSlug);
        command.setKind(kind);
        command.setStartEpochSeconds(startEpochSeconds);
        command.setEndEpochSeconds(endEpochSeconds);
        command.setStepSeconds(stepSeconds);
        command.setPod(pod);

        MetricsResult result =
                awaiter.enqueueAndAwait(clusterName, CommandTypes.QUERY_METRICS, command, MetricsResult.class, TIMEOUT);

        var response = new MetricsResponse();
        if (!result.isAvailable()) {
            String message = result.getMessage();
            response.setMessage(message != null && !message.isBlank() ? message : MetricsService.NOT_AVAILABLE);
            return response;
        }
        for (MetricsResultSeries s : result.getSeries()) {
            var series = new MetricsResponse.MetricsSeries();
            series.setPod(s.getPod());
            for (MetricsResultSample sample : s.getValues()) {
                var point = new MetricsResponse.MetricsSeries.MetricsDataPoint();
                point.setTimestamp(sample.getTimestamp());
                point.setValue(sample.getValue());
                series.getValues().add(point);
            }
            response.getSeries().add(series);
        }
        return response;
    }
}

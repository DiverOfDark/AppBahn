package eu.appbahn.platform.tunnel.events;

import eu.appbahn.platform.api.resource.LogResponse;
import eu.appbahn.platform.api.tunnel.LogsResult;
import eu.appbahn.platform.api.tunnel.LogsResultLine;
import eu.appbahn.platform.api.tunnel.QueryLogs;
import eu.appbahn.platform.resource.service.LogsService;
import eu.appbahn.platform.resource.service.LogsSupplier;
import eu.appbahn.platform.tunnel.command.CommandResponseAwaiter;
import eu.appbahn.platform.tunnel.command.CommandTypes;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

/**
 * Tunnel-backed {@link LogsSupplier}: enqueues a {@link QueryLogs} command, blocks for the
 * operator's ack, and maps the {@link LogsResult} payload onto the public {@link LogResponse}
 * shape. When the operator reports no usable provider ({@code available=false}), the supplier
 * returns the graceful "not available" response, preferring the operator's distinct reason ("no log
 * provider configured" vs "no Victoria Logs URL configured") and falling back to
 * {@link LogsService#NOT_AVAILABLE}.
 */
@Service
public class TunnelLogsSupplier implements LogsSupplier {

    /**
     * A LogsQL query is heavier than a pod list — a ten-second budget covers one slow round-trip
     * without wedging the API thread for the full pending-command sweeper window.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final CommandResponseAwaiter awaiter;

    public TunnelLogsSupplier(CommandResponseAwaiter awaiter) {
        this.awaiter = awaiter;
    }

    @Override
    public LogResponse fetch(
            String clusterName,
            String namespace,
            String resourceSlug,
            String pod,
            String container,
            String deploymentId,
            long sinceEpochSeconds,
            int limit) {
        var command = new QueryLogs();
        command.setNamespace(namespace);
        command.setResourceSlug(resourceSlug);
        command.setPod(pod);
        command.setContainer(container);
        command.setDeploymentId(deploymentId);
        command.setSinceEpochSeconds(sinceEpochSeconds);
        command.setLimit(limit);

        LogsResult result =
                awaiter.enqueueAndAwait(clusterName, CommandTypes.QUERY_LOGS, command, LogsResult.class, TIMEOUT);

        var response = new LogResponse();
        if (!result.isAvailable()) {
            String reason = result.getMessage();
            response.setMessage(
                    reason != null && !reason.isBlank() ? "Logs not available — " + reason : LogsService.NOT_AVAILABLE);
            return response;
        }
        for (LogsResultLine line : result.getLines()) {
            var entry = new LogResponse.LogLine();
            entry.setTimestamp(
                    Instant.ofEpochMilli((long) (line.getTimestamp() * 1000.0)).atOffset(ZoneOffset.UTC));
            entry.setMessage(line.getMessage());
            entry.setPod(line.getPod());
            entry.setContainer(line.getContainer());
            response.getLines().add(entry);
        }
        return response;
    }
}

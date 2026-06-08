package eu.appbahn.platform.tunnel.events;

import eu.appbahn.platform.api.resource.LogStreamLogFrame;
import eu.appbahn.platform.api.tunnel.LogsResult;
import eu.appbahn.platform.api.tunnel.LogsResultLine;
import eu.appbahn.platform.api.tunnel.QueryLogs;
import eu.appbahn.platform.resource.service.LogStreamSupplier;
import eu.appbahn.platform.tunnel.command.CommandResponseAwaiter;
import eu.appbahn.platform.tunnel.command.CommandTypes;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Tunnel-backed {@link LogStreamSupplier}: each {@code tail} call enqueues a {@link QueryLogs}
 * command bounded to lines newer than the cursor, blocks for the operator's ack, and maps the
 * {@link LogsResult} payload onto {@link LogStreamLogFrame}s. The streaming service advances the
 * cursor across rounds, so the operator-side query stays a stateless one-shot.
 *
 * <p>Errors (operator down, query failure) are swallowed to an empty list — a transient failure
 * must not tear the SSE stream down; the next poll round retries.
 */
@Service
public class TunnelLogStreamSupplier implements LogStreamSupplier {

    private static final Logger log = LoggerFactory.getLogger(TunnelLogStreamSupplier.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final CommandResponseAwaiter awaiter;

    public TunnelLogStreamSupplier(CommandResponseAwaiter awaiter) {
        this.awaiter = awaiter;
    }

    @Override
    public List<LogStreamLogFrame> tail(
            String clusterName,
            String namespace,
            String resourceSlug,
            String pod,
            String container,
            long sinceEpochSeconds,
            int limit) {
        var command = new QueryLogs();
        command.setNamespace(namespace);
        command.setResourceSlug(resourceSlug);
        command.setPod(pod);
        command.setContainer(container);
        command.setSinceEpochSeconds(sinceEpochSeconds);
        command.setLimit(limit);

        LogsResult result;
        try {
            result = awaiter.enqueueAndAwait(clusterName, CommandTypes.QUERY_LOGS, command, LogsResult.class, TIMEOUT);
        } catch (Exception e) {
            log.debug("log tail for {}/{} failed this round: {}", namespace, resourceSlug, e.getMessage());
            return List.of();
        }
        if (!result.isAvailable()) {
            return List.of();
        }

        List<LogStreamLogFrame> frames = new ArrayList<>(result.getLines().size());
        for (LogsResultLine line : result.getLines()) {
            // The operator's _time bound is inclusive; drop lines at exactly the cursor so we don't
            // re-emit the last line of the previous round.
            if (line.getTimestamp() <= sinceEpochSeconds) {
                continue;
            }
            var frame = new LogStreamLogFrame();
            frame.setTimestamp(
                    Instant.ofEpochMilli((long) (line.getTimestamp() * 1000.0)).atOffset(ZoneOffset.UTC));
            frame.setMessage(line.getMessage());
            frame.setPod(line.getPod());
            frame.setContainer(line.getContainer());
            frames.add(frame);
        }
        return frames;
    }
}

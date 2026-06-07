package eu.appbahn.platform.api.tunnel;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Log lines returned by the operator in response to {@link QueryLogs}. When the operator has no log
 * provider configured, {@link #available} is {@code false}, {@link #lines} is empty, and
 * {@link #message} carries the operator's reason (distinguishing "no log provider configured" from
 * "no Victoria Logs URL configured") — the platform surfaces that as the graceful "not available"
 * response shown in the console.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LogsResult extends CommandResponsePayload {

    /** False when the operator has no usable Victoria Logs endpoint configured. */
    private boolean available;

    /** Operator-supplied reason when {@link #available} is {@code false}; null otherwise. */
    private String message;

    private List<LogsResultLine> lines = new ArrayList<>();

    public LogsResult() {
        setType("logs-result");
    }
}

package eu.appbahn.platform.api.tunnel;

import lombok.Data;
import org.springframework.lang.Nullable;

/** One log line inside a {@link LogsResult}. */
@Data
public class LogsResultLine {

    /** Line time as a Unix epoch second (fractional for sub-second precision). */
    private double timestamp;

    @Nullable
    private String message;

    @Nullable
    private String pod;

    @Nullable
    private String container;
}

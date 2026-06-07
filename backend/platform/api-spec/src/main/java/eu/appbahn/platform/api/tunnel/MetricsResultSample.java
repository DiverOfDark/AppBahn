package eu.appbahn.platform.api.tunnel;

import lombok.Data;

/** One {@code [timestamp, value]} sample inside a {@link MetricsResultSeries}. */
@Data
public class MetricsResultSample {

    /** Sample time as a Unix epoch second. */
    private double timestamp;

    private double value;
}

package eu.appbahn.platform.api.tunnel;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

/** One pod's time-series inside a {@link MetricsResult}. */
@Data
public class MetricsResultSeries {

    @Nullable
    private String pod;

    /** Time-ordered samples for this pod. */
    private List<MetricsResultSample> values = new ArrayList<>();
}

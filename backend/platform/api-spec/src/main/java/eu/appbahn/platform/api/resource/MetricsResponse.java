package eu.appbahn.platform.api.resource;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class MetricsResponse {

    @Valid
    private List<MetricsSeries> series = new ArrayList<>();

    @Valid
    @Nullable
    private OffsetDateTime start;

    @Valid
    @Nullable
    private OffsetDateTime end;

    @Nullable
    private Integer step;

    @Data
    public static class MetricsSeries {

        @Nullable
        private String pod;

        @Valid
        private List<MetricsDataPoint> values = new ArrayList<>();

        @Data
        public static class MetricsDataPoint {

            @Nullable
            private Double timestamp;

            @Nullable
            private Double value;
        }
    }
}

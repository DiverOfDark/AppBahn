package eu.appbahn.platform.api.resource;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class LogResponse {

    @Valid
    private List<LogLine> lines = new ArrayList<>();

    /**
     * Set only on graceful degradation — e.g. {@code "Logs not available — no log provider
     * configured"}. Null when a provider answered (lines may still be empty).
     */
    @Nullable
    private String message;

    @Data
    public static class LogLine {

        @Valid
        @Nullable
        private OffsetDateTime timestamp;

        @Nullable
        private String message;

        @Nullable
        private String pod;

        @Nullable
        private String container;
    }
}

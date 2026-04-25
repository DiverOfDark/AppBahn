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

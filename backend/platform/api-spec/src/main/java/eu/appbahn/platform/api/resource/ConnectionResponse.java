package eu.appbahn.platform.api.resource;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class ConnectionResponse {

    @Valid
    private List<ConnectionEntry> entries = new ArrayList<>();

    @Data
    public static class ConnectionEntry {

        @Nullable
        private String key;

        @Nullable
        private String value;

        @Nullable
        private Boolean secret;
    }
}

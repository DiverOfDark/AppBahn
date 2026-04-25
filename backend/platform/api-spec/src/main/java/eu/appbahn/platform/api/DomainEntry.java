package eu.appbahn.platform.api;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class DomainEntry {

    @Nullable
    private String domain;

    @Nullable
    private Integer port;

    @Nullable
    private String status;
}

package eu.appbahn.platform.api;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class AuditFieldChange {

    @Nullable
    private String field;

    @Nullable
    private String oldValue;

    @Nullable
    private String newValue;
}

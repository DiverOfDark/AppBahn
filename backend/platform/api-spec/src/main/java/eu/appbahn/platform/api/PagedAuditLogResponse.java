package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PagedAuditLogResponse extends PagedResponse {

    @Valid
    private List<AuditLogEntry> content = new ArrayList<>();
}

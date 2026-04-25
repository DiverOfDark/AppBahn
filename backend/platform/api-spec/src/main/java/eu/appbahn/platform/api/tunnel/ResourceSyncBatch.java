package eu.appbahn.platform.api.tunnel;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceSyncBatch extends OperatorEvent {

    @Valid
    private List<ResourceSyncItem> items = new ArrayList<>();

    public ResourceSyncBatch() {
        setType("resource-sync-batch");
    }
}

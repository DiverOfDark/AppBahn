package eu.appbahn.platform.api.tunnel;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceDeletedBatch extends OperatorEvent {

    private List<String> resourceSlugs = new ArrayList<>();

    public ResourceDeletedBatch() {
        setType("resource-deleted-batch");
    }
}

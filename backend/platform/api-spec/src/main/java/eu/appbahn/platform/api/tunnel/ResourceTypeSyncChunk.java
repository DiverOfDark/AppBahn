package eu.appbahn.platform.api.tunnel;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceTypeSyncChunk extends OperatorEvent {

    private String syncSessionId;
    private int chunkIndex;
    private List<String> availableTypes = new ArrayList<>();
    private boolean complete;

    public ResourceTypeSyncChunk() {
        setType("resource-type-sync-chunk");
    }
}

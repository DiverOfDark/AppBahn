package eu.appbahn.platform.api.tunnel;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FullResourceSyncChunk extends OperatorEvent {

    private String syncSessionId;
    private int chunkIndex;

    @Valid
    private List<ResourceSyncItem> items = new ArrayList<>();

    /** True on the last chunk; the platform commits the set-diff when it sees this flag. */
    private boolean complete;

    public FullResourceSyncChunk() {
        setType("full-resource-sync-chunk");
    }
}

package eu.appbahn.platform.api.tunnel;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImageSourceSyncBatch extends OperatorEvent {

    @Valid
    private List<ImageSourceSyncItem> items = new ArrayList<>();

    public ImageSourceSyncBatch() {
        setType("image-source-sync-batch");
    }
}

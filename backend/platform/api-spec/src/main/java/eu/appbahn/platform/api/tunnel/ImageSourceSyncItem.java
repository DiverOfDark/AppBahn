package eu.appbahn.platform.api.tunnel;

import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import jakarta.validation.Valid;
import lombok.Data;

/**
 * Operator-emitted snapshot of one ImageSource CR. {@code imageSource} carries the full CRD
 * (metadata + spec + status); {@code generation} / {@code resourceVersion} ride alongside
 * as explicit envelope fields for staleness checks without deserialising the metadata.
 */
@Data
public class ImageSourceSyncItem {

    @Valid
    private ImageSourceCrd imageSource;

    private long generation;
    private String resourceVersion;
}

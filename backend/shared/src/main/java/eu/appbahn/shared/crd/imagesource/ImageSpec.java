package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Data;

/** Manually pinned image reference. {@code runCommand} is an optional override. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageSpec {
    private String ref;
    private List<String> runCommand;
}

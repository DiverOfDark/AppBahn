package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.crd.generator.annotation.PrinterColumn;
import io.fabric8.generator.annotation.Required;
import lombok.Data;

/**
 * Flat tagged-union spec: {@link #type} discriminates which of {@link #git} / {@link #image}
 * is meaningful. Both sub-blocks are nullable — the operator validates "exactly one matches"
 * and surfaces a {@code ConfigInvalid} condition if the spec is malformed.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageSourceSpec {

    @PrinterColumn(name = "TYPE", priority = 1)
    @Required
    private ImageSourceType type;

    private ImageSourceGitSpec git;
    private ImageSpec image;
    private ImageSourceBuildSpec build;
    private ImageSourceTrigger trigger;
}

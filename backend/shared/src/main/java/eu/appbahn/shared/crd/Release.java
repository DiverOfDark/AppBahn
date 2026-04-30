package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Resource → ImageSource binding. The operator reads the referenced sibling ImageSource's
 * {@code status.latestArtifact} and renders the pod template from it. Same namespace only;
 * cross-namespace promotion goes through a {@code type: imageSource} ImageSource chain.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Release {

    private FromImageSource fromImageSource;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FromImageSource {
        private String name;
    }
}

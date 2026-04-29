package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.Data;

/** K8s-style condition entry. Status is "True" / "False" / "Unknown". */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageSourceCondition {
    private String type;
    private String status;
    private String reason;
    private String message;
    private Instant lastTransitionTime;
}

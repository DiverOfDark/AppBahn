package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageSourceTrigger {
    private ImageSourcePoll poll;
}

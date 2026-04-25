package eu.appbahn.platform.api.resource;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateExposureRequest {

    @NotNull
    private Integer port;

    @NotNull
    private Integer ttlMinutes;
}

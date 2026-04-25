package eu.appbahn.platform.api.resource;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddDomainRequest {

    @NotNull
    private String domain;

    @NotNull
    private Integer port;
}

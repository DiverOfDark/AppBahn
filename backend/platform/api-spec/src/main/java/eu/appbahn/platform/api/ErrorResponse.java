package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class ErrorResponse {

    @NotNull
    private Integer status;

    @NotNull
    private String error;

    @NotNull
    private String message;

    @Valid
    private List<String> details = new ArrayList<>();

    @Nullable
    private Integer current;

    @Nullable
    private Integer limit;

    @Nullable
    private String dimension;

    @Nullable
    private String level;
}

package eu.appbahn.platform.api.tunnel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class PushEventsRequest {

    @NotBlank
    private String clusterName;

    @Nullable
    private String sessionId;

    @Valid
    private List<OperatorEvent> events = new ArrayList<>();
}

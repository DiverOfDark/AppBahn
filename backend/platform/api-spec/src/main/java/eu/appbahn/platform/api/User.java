package eu.appbahn.platform.api;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class User {

    @Valid
    @Nullable
    private UUID id;

    @Nullable
    private String email;

    @Nullable
    private String oidcSubjectId;
}

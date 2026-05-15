package eu.appbahn.platform.api.user;

import java.util.UUID;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class CurrentUserResponse {

    private UUID id;

    private String email;

    @Nullable
    private String name;

    @Nullable
    private String avatarUrl;
}

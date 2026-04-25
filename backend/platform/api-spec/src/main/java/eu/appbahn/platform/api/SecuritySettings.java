package eu.appbahn.platform.api;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class SecuritySettings {

    @Nullable
    private String runtimeClassName;
}

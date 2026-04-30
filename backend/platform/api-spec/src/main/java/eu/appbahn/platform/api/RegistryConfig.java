package eu.appbahn.platform.api;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class RegistryConfig {

    @Nullable
    private String url;

    @Nullable
    private CredentialRef credentialRef;

    @Data
    public static class CredentialRef {
        @Nullable
        private String secretName;
    }
}

package eu.appbahn.platform.api;

import eu.appbahn.shared.crd.CredentialRef;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class RegistryConfig {

    @Nullable
    private String url;

    @Nullable
    private CredentialRef credentialRef;
}

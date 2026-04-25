package eu.appbahn.platform.api.tunnel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.lang.Nullable;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdmissionCacheMissReport extends OperatorEvent {

    private String namespace;

    @Nullable
    private String userOidcSubject;

    @Nullable
    private String reason;

    public AdmissionCacheMissReport() {
        setType("admission-cache-miss-report");
    }
}

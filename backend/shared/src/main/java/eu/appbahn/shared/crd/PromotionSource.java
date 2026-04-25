package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PromotionSource implements Source {

    private String type;
    private String pollInterval;
    private Boolean webhookEnabled;
    private String sourceEnvironment;
    private String sourceResource;
    private Boolean autoPromote;
}

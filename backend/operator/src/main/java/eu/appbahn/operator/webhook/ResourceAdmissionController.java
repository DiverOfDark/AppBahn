package eu.appbahn.operator.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Validating admission webhook for Resource CRDs. Currently allows all requests — real validation
 * (quota checks, identity mapping) will be added in Sprint 6.
 */
@RestController
public class ResourceAdmissionController {

    private static final Logger log = LoggerFactory.getLogger(ResourceAdmissionController.class);
    private static final String API_VERSION = "admission.k8s.io/v1";
    private static final String KIND = "AdmissionReview";

    @PostMapping(
            path = "/validate-appbahn-eu-v1-resource",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AdmissionReview validate(@RequestBody AdmissionReview review) {
        AdmissionRequest request = review.request();
        log.debug(
                "Admission review: {} {} {}/{}",
                request.operation(),
                request.uid(),
                request.namespace(),
                request.name());

        return new AdmissionReview(API_VERSION, KIND, null, new AdmissionResponse(request.uid(), true));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdmissionReview(
            String apiVersion, String kind, AdmissionRequest request, AdmissionResponse response) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdmissionRequest(String uid, String operation, String name, String namespace) {}

    public record AdmissionResponse(String uid, boolean allowed) {}
}

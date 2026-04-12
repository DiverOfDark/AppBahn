package eu.appbahn.operator.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    private final ObjectMapper objectMapper;

    public ResourceAdmissionController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostMapping(
            path = "/validate-appbahn-eu-v1-resource",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode validate(@RequestBody JsonNode admissionReview) {
        String uid = admissionReview.path("request").path("uid").asText();
        String operation = admissionReview.path("request").path("operation").asText();
        String name = admissionReview.path("request").path("name").asText();
        String namespace = admissionReview.path("request").path("namespace").asText();

        log.debug("Admission review: {} {} {}/{}", operation, uid, namespace, name);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("apiVersion", "admission.k8s.io/v1");
        response.put("kind", "AdmissionReview");

        ObjectNode responseBody = response.putObject("response");
        responseBody.put("uid", uid);
        responseBody.put("allowed", true);

        return response;
    }
}

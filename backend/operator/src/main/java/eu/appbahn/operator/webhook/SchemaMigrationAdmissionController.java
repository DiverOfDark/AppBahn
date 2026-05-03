package eu.appbahn.operator.webhook;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.appbahn.operator.migrations.CrMigrationStartupSweep;
import eu.appbahn.operator.migrations.MigrationRunner;
import eu.appbahn.shared.Labels;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionResponse;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionResponseBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReviewBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mutating admission webhook for AppBahn CRDs (Resource, ImageSource, ResourceTypeDefinition).
 * Catches the path that the startup sweep can't: a user {@code kubectl apply}-ing an old-shape
 * YAML at runtime. The webhook runs the {@link MigrationRunner} chain on the incoming spec and
 * returns a JSON Patch that mutates {@code spec} + the {@code appbahn.eu/resource-schema-version}
 * annotation in one shot — by the time the validating webhook + the operator's reconciler see
 * the object it is already at the current schema version.
 *
 * <p>This webhook does <em>not</em> short-circuit any of the validation rules in {@link
 * ResourceAdmissionController}; mutating webhooks always run before validating ones (K8s
 * admission contract), so the validating webhook still gets the final say on quotas/RBAC.
 */
@RestController
public class SchemaMigrationAdmissionController {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationAdmissionController.class);

    private final MigrationRunner migrationRunner;
    private final ObjectMapper objectMapper;
    private final Map<String, CrMigrationStartupSweep.KnownKind> knownKindByName;

    public SchemaMigrationAdmissionController(MigrationRunner migrationRunner, ObjectMapper objectMapper) {
        this.migrationRunner = migrationRunner;
        this.objectMapper = objectMapper;
        Map<String, CrMigrationStartupSweep.KnownKind> map = new HashMap<>();
        for (CrMigrationStartupSweep.KnownKind kk : CrMigrationStartupSweep.KNOWN_KINDS) {
            map.put(kk.kind(), kk);
        }
        this.knownKindByName = Map.copyOf(map);
    }

    @PostMapping(
            path = "/mutate-appbahn-eu-v1-schema",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AdmissionReview mutate(@RequestBody AdmissionReview review) {
        AdmissionRequest request = review.getRequest();
        if (request == null) {
            return reviewOf(allowUnchanged(null));
        }
        String op = request.getOperation();
        if ("DELETE".equalsIgnoreCase(op)) {
            return reviewOf(allowUnchanged(request.getUid()));
        }
        String kind = request.getKind() != null ? request.getKind().getKind() : null;
        CrMigrationStartupSweep.KnownKind kk = kind == null ? null : knownKindByName.get(kind);
        if (kk == null) {
            return reviewOf(allowUnchanged(request.getUid()));
        }
        int currentVersion = migrationRunner.currentVersion(kind);
        Object raw = request.getObject();
        if (raw == null) {
            return reviewOf(allowUnchanged(request.getUid()));
        }
        try {
            JsonNode tree = objectMapper.valueToTree(raw);
            ObjectNode spec = extractSpec(tree);
            if (spec == null) {
                return reviewOf(allowUnchanged(request.getUid()));
            }
            Integer annotated = migrationRunner.readVersionAnnotation(tree, Labels.RESOURCE_SCHEMA_VERSION_KEY);
            int fromVersion;
            boolean wasMissing = annotated == null;
            if (wasMissing) {
                fromVersion = migrationRunner.detectFromVersion(kind, spec, kk.specModel());
            } else {
                fromVersion = annotated;
            }
            if (fromVersion >= currentVersion && !wasMissing) {
                return reviewOf(allowUnchanged(request.getUid()));
            }
            if (fromVersion < currentVersion) {
                migrationRunner.migrate(kind, spec, fromVersion);
            }
            // Build the JSON Patch: replace spec, set annotation, ensure annotations map exists.
            return reviewOf(buildPatchResponse(request.getUid(), tree, spec, currentVersion));
        } catch (Exception e) {
            log.warn(
                    "Schema-migration webhook failed for {}/{}; passing through unchanged: {}",
                    request.getNamespace(),
                    request.getName(),
                    e.getMessage());
            return reviewOf(allowUnchanged(request.getUid()));
        }
    }

    private ObjectNode extractSpec(JsonNode tree) {
        JsonNode spec = tree.get("spec");
        if (spec == null || spec.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!spec.isObject()) {
            return null;
        }
        return (ObjectNode) spec;
    }

    private AdmissionResponse buildPatchResponse(String uid, JsonNode tree, ObjectNode migratedSpec, int newVersion) {
        boolean annotationsExisted =
                tree.has("metadata") && tree.get("metadata").has("annotations");
        ObjectNode[] patches = new ObjectNode[annotationsExisted ? 2 : 3];
        int idx = 0;
        if (!annotationsExisted) {
            ObjectNode addAnnotations = objectMapper.createObjectNode();
            addAnnotations.put("op", "add");
            addAnnotations.put("path", "/metadata/annotations");
            addAnnotations.set("value", objectMapper.createObjectNode());
            patches[idx++] = addAnnotations;
        }
        ObjectNode setAnnotation = objectMapper.createObjectNode();
        setAnnotation.put("op", "add");
        setAnnotation.put("path", "/metadata/annotations/" + jsonPointerEscape(Labels.RESOURCE_SCHEMA_VERSION_KEY));
        setAnnotation.put("value", Integer.toString(newVersion));
        patches[idx++] = setAnnotation;

        ObjectNode replaceSpec = objectMapper.createObjectNode();
        replaceSpec.put("op", "replace");
        replaceSpec.put("path", "/spec");
        replaceSpec.set("value", migratedSpec);
        patches[idx++] = replaceSpec;

        try {
            byte[] patchBytes = objectMapper.writeValueAsBytes(List.of(patches));
            String encoded = Base64Variants.MIME_NO_LINEFEEDS.encode(patchBytes);
            return new AdmissionResponseBuilder()
                    .withUid(uid)
                    .withAllowed(true)
                    .withPatchType("JSONPatch")
                    .withPatch(encoded)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to encode admission patch; passing through unchanged: {}", e.getMessage());
            return allowUnchanged(uid);
        }
    }

    /** Escape a JSON Pointer reference token per RFC 6901 (~ → ~0, / → ~1). */
    private static String jsonPointerEscape(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    private AdmissionResponse allowUnchanged(String uid) {
        return new AdmissionResponseBuilder().withUid(uid).withAllowed(true).build();
    }

    private AdmissionReview reviewOf(AdmissionResponse response) {
        return new AdmissionReviewBuilder().withResponse(response).build();
    }
}

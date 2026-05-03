package eu.appbahn.operator.migrations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.ResourceSpec;
import eu.appbahn.shared.crd.ResourceTypeDefinitionCrd;
import eu.appbahn.shared.crd.ResourceTypeDefinitionSpec;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourceSpec;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * On operator startup, scan every Resource / ImageSource / ResourceTypeDefinition CR via the raw
 * fabric8 generic client, run the {@link MigrationRunner} chain on each one whose
 * schema-version annotation lags behind, and patch the migrated spec + bumped annotation back
 * via server-side apply.
 *
 * <p>Runs as an {@link EventListener} on {@link ApplicationReadyEvent} with {@link
 * Ordered#HIGHEST_PRECEDENCE} so it executes <em>before</em> JOSDK's {@code OperatorStarter}
 * (declared at order 1) registers informers — by the time the reconciler watches start, every
 * CR is already at the current schema version. {@code FullSyncService} runs at order 100, after
 * the operator is up, and picks up any post-migration spec changes naturally.
 */
@Component
public class CrMigrationStartupSweep {

    private static final Logger log = LoggerFactory.getLogger(CrMigrationStartupSweep.class);

    /** All known appbahn.eu/v1 kinds the operator owns. */
    public record KnownKind(String kind, String pluralName, boolean namespaced, Class<?> specModel) {}

    public static final List<KnownKind> KNOWN_KINDS = List.of(
            new KnownKind("Resource", "resources", true, ResourceSpec.class),
            new KnownKind("ImageSource", "imagesources", true, ImageSourceSpec.class),
            new KnownKind(
                    "ResourceTypeDefinition", "resourcetypedefinitions", false, ResourceTypeDefinitionSpec.class));

    private static final String GROUP = "appbahn.eu";
    private static final String VERSION = "v1";

    private final KubernetesClient kubernetesClient;
    private final MigrationRunner migrationRunner;
    private final ObjectMapper objectMapper;

    public CrMigrationStartupSweep(
            KubernetesClient kubernetesClient, MigrationRunner migrationRunner, ObjectMapper objectMapper) {
        // Sanity check: all kinds we sweep must match the @Group/@Version on the CRD classes
        // they back, otherwise the generic-client list path would silently see an empty result.
        assertGroupVersion(ResourceCrd.class);
        assertGroupVersion(ImageSourceCrd.class);
        assertGroupVersion(ResourceTypeDefinitionCrd.class);
        this.kubernetesClient = kubernetesClient;
        this.migrationRunner = migrationRunner;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onStartup() {
        run();
    }

    /** Public entry point so the test suite can drive it without mucking with event ordering. */
    public SweepReport run() {
        Map<String, Integer> migratedByKind = new LinkedHashMap<>();
        Map<String, Integer> stampedByKind = new LinkedHashMap<>();
        Map<String, Integer> totalsByKind = new LinkedHashMap<>();
        for (KnownKind kk : KNOWN_KINDS) {
            int currentVersion = migrationRunner.currentVersion(kk.kind());
            ResourceDefinitionContext rdc = new ResourceDefinitionContext.Builder()
                    .withGroup(GROUP)
                    .withVersion(VERSION)
                    .withKind(kk.kind())
                    .withPlural(kk.pluralName())
                    .withNamespaced(kk.namespaced())
                    .build();
            int migrated = 0;
            int stamped = 0;
            int total = 0;
            try {
                GenericKubernetesResourceList list = kk.namespaced()
                        ? kubernetesClient
                                .genericKubernetesResources(rdc)
                                .inAnyNamespace()
                                .list()
                        : kubernetesClient.genericKubernetesResources(rdc).list();
                if (list == null || list.getItems() == null) {
                    continue;
                }
                for (GenericKubernetesResource item : list.getItems()) {
                    total++;
                    SweepOutcome outcome = sweepOne(rdc, item, kk, currentVersion);
                    if (outcome == SweepOutcome.MIGRATED) {
                        migrated++;
                    } else if (outcome == SweepOutcome.STAMPED) {
                        stamped++;
                    }
                }
            } catch (Exception e) {
                log.warn("Sweep of {} CRs failed: {}", kk.kind(), e.getMessage());
            }
            migratedByKind.put(kk.kind(), migrated);
            stampedByKind.put(kk.kind(), stamped);
            totalsByKind.put(kk.kind(), total);
            if (migrated > 0 || stamped > 0) {
                log.info(
                        "CR migration sweep — kind={} total={} migrated={} stamped={} (target v{})",
                        kk.kind(),
                        total,
                        migrated,
                        stamped,
                        currentVersion);
            } else {
                log.debug("CR migration sweep — kind={} total={} no-op (target v{})", kk.kind(), total, currentVersion);
            }
        }
        return new SweepReport(migratedByKind, stampedByKind, totalsByKind);
    }

    private SweepOutcome sweepOne(
            ResourceDefinitionContext rdc, GenericKubernetesResource item, KnownKind kk, int currentVersion) {
        if (currentVersion == 0) {
            return SweepOutcome.NOOP;
        }
        JsonNode tree = objectMapper.valueToTree(item);
        Integer annotated = migrationRunner.readVersionAnnotation(tree, Labels.RESOURCE_SCHEMA_VERSION_KEY);
        ObjectNode spec = extractSpec(tree);
        if (spec == null) {
            return SweepOutcome.NOOP;
        }
        int fromVersion;
        boolean wasMissing = annotated == null;
        if (wasMissing) {
            fromVersion = migrationRunner.detectFromVersion(kk.kind(), spec, kk.specModel());
        } else {
            fromVersion = annotated;
        }
        if (fromVersion >= currentVersion) {
            if (wasMissing) {
                stampVersionOnly(rdc, item, currentVersion);
                return SweepOutcome.STAMPED;
            }
            return SweepOutcome.NOOP;
        }
        MigrationRunner.MigratedResult result = migrationRunner.migrate(kk.kind(), spec, fromVersion);
        applyMigratedSpec(rdc, item, result.migratedSpec(), result.newVersion());
        return SweepOutcome.MIGRATED;
    }

    private ObjectNode extractSpec(JsonNode tree) {
        JsonNode spec = tree.get("spec");
        if (spec == null || spec.isNull()) {
            // Empty spec: nothing to migrate. We still want to stamp the annotation so
            // future sweeps short-circuit; treat it as an empty object.
            return objectMapper.createObjectNode();
        }
        if (!spec.isObject()) {
            return null;
        }
        return (ObjectNode) spec;
    }

    private void applyMigratedSpec(
            ResourceDefinitionContext rdc,
            GenericKubernetesResource original,
            ObjectNode migratedSpec,
            int newVersion) {
        Map<String, Object> patched = buildPatchedItem(original, migratedSpec, newVersion);
        GenericKubernetesResource patchedResource = objectMapper.convertValue(patched, GenericKubernetesResource.class);
        replace(rdc, patchedResource);
    }

    private void stampVersionOnly(ResourceDefinitionContext rdc, GenericKubernetesResource original, int newVersion) {
        Map<String, Object> patched = buildPatchedItem(original, /* migratedSpec = */ null, newVersion);
        GenericKubernetesResource patchedResource = objectMapper.convertValue(patched, GenericKubernetesResource.class);
        replace(rdc, patchedResource);
    }

    private Map<String, Object> buildPatchedItem(
            GenericKubernetesResource original, ObjectNode migratedSpec, int newVersion) {
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = objectMapper.convertValue(original, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) raw.computeIfAbsent("metadata", k -> new HashMap<>());
        @SuppressWarnings("unchecked")
        Map<String, Object> annotations =
                (Map<String, Object>) metadata.computeIfAbsent("annotations", k -> new HashMap<>());
        annotations.put(Labels.RESOURCE_SCHEMA_VERSION_KEY, Integer.toString(newVersion));
        if (migratedSpec != null) {
            raw.put("spec", objectMapper.convertValue(migratedSpec, Map.class));
        }
        return raw;
    }

    private void replace(ResourceDefinitionContext rdc, GenericKubernetesResource patchedResource) {
        String namespace = patchedResource.getMetadata().getNamespace();
        var generic = kubernetesClient.genericKubernetesResources(rdc);
        if (namespace != null && !namespace.isBlank()) {
            generic.inNamespace(namespace).resource(patchedResource).update();
        } else {
            generic.resource(patchedResource).update();
        }
    }

    private static void assertGroupVersion(Class<?> crdClass) {
        var group = crdClass.getAnnotation(io.fabric8.kubernetes.model.annotation.Group.class);
        var version = crdClass.getAnnotation(io.fabric8.kubernetes.model.annotation.Version.class);
        if (group == null || version == null) {
            throw new IllegalStateException("CRD class " + crdClass.getName() + " missing @Group/@Version");
        }
        if (!GROUP.equals(group.value()) || !VERSION.equals(version.value())) {
            throw new IllegalStateException("CRD class " + crdClass.getName() + " has unexpected group/version "
                    + group.value() + "/" + version.value());
        }
    }

    private enum SweepOutcome {
        MIGRATED,
        STAMPED,
        NOOP
    }

    /** Per-kind counts of what the sweep did, for tests + log/metric correlation. */
    public record SweepReport(
            Map<String, Integer> migratedByKind,
            Map<String, Integer> stampedByKind,
            Map<String, Integer> totalsByKind) {}
}

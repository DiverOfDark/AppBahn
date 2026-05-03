package eu.appbahn.shared;

import eu.appbahn.shared.crd.ResourceCrd;
import java.util.Map;

public final class Labels {

    private Labels() {}

    public static final String MANAGED_BY_KEY = "app.kubernetes.io/managed-by";
    public static final String MANAGED_BY_VALUE = "appbahn";
    public static final String RESOURCE_KEY = "appbahn.eu/resource";
    public static final String ENVIRONMENT_SLUG_KEY = "appbahn.eu/environment-slug";

    /** Pod-template annotation that pins the rolled-out artifact when the release path is in use. */
    public static final String RELEASE_IMAGE_REF_KEY = "appbahn.eu/release-image-ref";

    /** Pod-template annotation that forces a re-roll on {@code restartGeneration} bumps. */
    public static final String RESTART_GENERATION_KEY = "appbahn.eu/restart-generation";

    /** Marks Jobs created by the operator as builds for a specific ImageSource. */
    public static final String IMAGE_SOURCE_KEY = "appbahn.eu/image-source";

    /** Tags a build Job with the source commit SHA it builds (for diagnostic / debugging). */
    public static final String BUILD_COMMIT_KEY = "appbahn.eu/build-commit";

    /** Tags a build Job with the platform deploymentId so events round-trip cleanly. */
    public static final String BUILD_DEPLOYMENT_ID_KEY = "appbahn.eu/build-deployment-id";

    /** Build mode constant — DRY's the {@code BuildMode} enum value into a label string. */
    public static final String BUILD_MODE_KEY = "appbahn.eu/build-mode";

    /**
     * Finalizer that blocks ImageSource deletion while downstream Resources still reference it.
     * The operator's {@link io.javaoperatorsdk.operator.api.reconciler.Cleaner} adds it on every
     * reconcile and only removes it (via {@code DeleteControl.defaultDelete()}) once the
     * same-cluster downstream check and the cross-cluster annotation check both come up empty.
     */
    public static final String FINALIZER_UPSTREAM_PROTECTION = "appbahn.eu/upstream-protection";

    /**
     * JSON-array annotation on an upstream ImageSource listing the cross-cluster downstreams that
     * point at it. Each entry is {@code {"cluster":"...","namespace":"...","name":"..."}}. The
     * platform's promotion broker maintains it via {@code ApplyResourceBundle}; the operator's
     * cleanup logic reads it to block deletion when downstreams from another cluster exist.
     */
    public static final String ANNOTATION_DOWNSTREAM_REFERENCES = "appbahn.eu/downstream-references";

    /**
     * Per-CR annotation recording which schema migration version the {@code spec} is currently
     * shaped for. Each CRD kind (Resource, ImageSource, ResourceTypeDefinition) evolves
     * independently — the same annotation key carries the kind-specific version number. The
     * operator's startup sweep + mutating admission webhook stamp/migrate this on every CR; the
     * absence of the annotation is treated either as "current" (if the typed model still
     * deserializes) or as "v0" (if it doesn't), see {@code MigrationRunner}.
     */
    public static final String RESOURCE_SCHEMA_VERSION_KEY = "appbahn.eu/resource-schema-version";

    public static final String RESOURCE_TYPE_DEPLOYMENT = "deployment";

    public static final int DEFAULT_REPLICAS = 1;

    public static final String CONTAINER_NAME = "app";
    public static final String INGRESS_PATH_TYPE = "Prefix";
    public static final String SERVICE_PROTOCOL = "TCP";
    public static final String SERVICE_TYPE_CLUSTER_IP = "ClusterIP";
    public static final String SERVICE_TYPE_LOAD_BALANCER = "LoadBalancer";

    public static final String DEFAULT_IMAGE_TAG = "latest";
    public static final String DEFAULT_CLUSTER_NAME = "local";
    public static final String EPHEMERAL_STORAGE_LIMIT = "1Gi";
    public static final String CERT_MANAGER_CLUSTER_ISSUER_ANNOTATION = "cert-manager.io/cluster-issuer";
    public static final String RESOURCE_KEY_CPU = "cpu";
    public static final String RESOURCE_KEY_MEMORY = "memory";
    public static final String RESOURCE_KEY_EPHEMERAL_STORAGE = "ephemeral-storage";

    public static Map<String, String> forResource(String resourceName) {
        return Map.of(MANAGED_BY_KEY, MANAGED_BY_VALUE, RESOURCE_KEY, resourceName);
    }

    public static Map<String, String> forResource(String resourceName, String envSlug) {
        return Map.of(
                MANAGED_BY_KEY, MANAGED_BY_VALUE,
                RESOURCE_KEY, resourceName,
                ENVIRONMENT_SLUG_KEY, envSlug);
    }

    /** Extract labels for a dependent resource from its primary CRD. */
    public static Map<String, String> forPrimary(ResourceCrd primary) {
        String name = primary.getMetadata().getName();
        String envSlug = primary.getMetadata().getLabels() != null
                ? primary.getMetadata().getLabels().get(ENVIRONMENT_SLUG_KEY)
                : null;
        return envSlug != null ? forResource(name, envSlug) : forResource(name);
    }
}

package eu.appbahn.shared;

import eu.appbahn.shared.crd.ResourceCrd;
import java.util.Map;

public final class Labels {

    private Labels() {}

    public static final String MANAGED_BY_KEY = "app.kubernetes.io/managed-by";
    public static final String MANAGED_BY_VALUE = "appbahn";
    public static final String RESOURCE_KEY = "appbahn.eu/resource";
    public static final String ENVIRONMENT_SLUG_KEY = "appbahn.eu/environment-slug";
    public static final String DEPLOYMENT_REVISION_KEY = "appbahn.eu/deployment-revision";

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

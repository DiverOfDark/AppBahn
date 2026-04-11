package eu.appbahn.platform.workspace.service;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.net.HttpURLConnection;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class NamespaceService {

    private static final Logger log = LoggerFactory.getLogger(NamespaceService.class);

    @Nullable
    private final KubernetesClient kubernetesClient;

    private final String namespacePrefix;

    public NamespaceService(
            @Nullable KubernetesClient kubernetesClient,
            @Value("${platform.namespace-prefix:abp}") String namespacePrefix) {
        this.kubernetesClient = kubernetesClient;
        this.namespacePrefix = namespacePrefix;
    }

    public String computeNamespace(String envSlug) {
        return namespacePrefix + "-" + envSlug;
    }

    public void createNamespace(String envSlug) {
        if (kubernetesClient == null) {
            log.debug("Kubernetes client not available, skipping namespace creation");
            return;
        }
        String namespaceName = computeNamespace(envSlug);
        try {
            var ns = new NamespaceBuilder()
                    .withNewMetadata()
                    .withName(namespaceName)
                    .withLabels(Map.of(
                            eu.appbahn.shared.Labels.MANAGED_BY_KEY,
                            eu.appbahn.shared.Labels.MANAGED_BY_VALUE,
                            eu.appbahn.shared.Labels.ENVIRONMENT_SLUG_KEY,
                            envSlug))
                    .endMetadata()
                    .build();
            kubernetesClient.namespaces().resource(ns).create();
            log.info("Created namespace: {}", namespaceName);
        } catch (KubernetesClientException e) {
            if (e.getCode() == HttpURLConnection.HTTP_CONFLICT) {
                log.info("Namespace {} already exists, skipping creation", namespaceName);
            } else {
                throw e;
            }
        }
    }

    public void deleteNamespace(String envSlug) {
        if (kubernetesClient == null) {
            log.debug("Kubernetes client not available, skipping namespace deletion");
            return;
        }
        String namespaceName = computeNamespace(envSlug);
        try {
            kubernetesClient.namespaces().withName(namespaceName).delete();
            log.info("Deleted namespace: {}", namespaceName);
        } catch (KubernetesClientException e) {
            if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                log.info("Namespace {} already deleted, skipping", namespaceName);
            } else {
                throw e;
            }
        }
    }
}

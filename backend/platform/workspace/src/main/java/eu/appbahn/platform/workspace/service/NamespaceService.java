package eu.appbahn.platform.workspace.service;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NamespaceService {

    private static final Logger log = LoggerFactory.getLogger(NamespaceService.class);

    @Nullable
    private final KubernetesClient kubernetesClient;
    private final String namespacePrefix;

    public NamespaceService(
            @Nullable KubernetesClient kubernetesClient,
            @Value("${platform.namespace-prefix:abp}") String namespacePrefix
    ) {
        this.kubernetesClient = kubernetesClient;
        this.namespacePrefix = namespacePrefix;
    }

    public void createNamespace(String envSlug) {
        if (kubernetesClient == null) {
            log.debug("Kubernetes client not available, skipping namespace creation");
            return;
        }
        String namespaceName = namespacePrefix + "-" + envSlug;
        try {
            var ns = new NamespaceBuilder()
                    .withNewMetadata()
                        .withName(namespaceName)
                        .withLabels(Map.of(
                                "app.kubernetes.io/managed-by", "appbahn",
                                "appbahn.eu/environment-slug", envSlug
                        ))
                    .endMetadata()
                    .build();
            kubernetesClient.namespaces().resource(ns).create();
            log.info("Created namespace: {}", namespaceName);
        } catch (Exception e) {
            log.warn("Failed to create namespace {}: {}", namespaceName, e.getMessage());
        }
    }

    public void deleteNamespace(String envSlug) {
        if (kubernetesClient == null) {
            log.debug("Kubernetes client not available, skipping namespace deletion");
            return;
        }
        String namespaceName = namespacePrefix + "-" + envSlug;
        try {
            kubernetesClient.namespaces().withName(namespaceName).delete();
            log.info("Deleted namespace: {}", namespaceName);
        } catch (Exception e) {
            log.warn("Failed to delete namespace {}: {}", namespaceName, e.getMessage());
        }
    }
}

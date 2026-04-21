package eu.appbahn.platform.workspace.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Computes the K8s namespace name for an environment slug. The K8s create/delete calls
 * themselves are routed through the operator tunnel via {@link NamespaceCrdClient}.
 */
@Service
public class NamespaceService {

    private final String namespacePrefix;

    public NamespaceService(@Value("${platform.namespace-prefix:abp}") String namespacePrefix) {
        this.namespacePrefix = namespacePrefix;
    }

    public String computeNamespace(String envSlug) {
        return namespacePrefix + "-" + envSlug;
    }
}

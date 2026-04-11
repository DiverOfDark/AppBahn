package eu.appbahn.platform.resource.service;

import eu.appbahn.shared.crd.ResourceCrd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/** No-op implementation used when no Kubernetes client is available (e.g. in tests). */
public class NoOpResourceCrdClient implements ResourceCrdClient {

    private static final Logger log = LoggerFactory.getLogger(NoOpResourceCrdClient.class);

    @Override
    public void create(ResourceCrd crd) {
        log.warn(
                "Kubernetes client not available — CRD not created for resource {}",
                crd.getMetadata().getName());
    }

    @Override
    public void update(ResourceCrd crd) {
        log.warn(
                "Kubernetes client not available — CRD not updated for resource {}",
                crd.getMetadata().getName());
    }

    @Override
    public void delete(ResourceCrd crd) {
        log.warn(
                "Kubernetes client not available — CRD not deleted for resource {}",
                crd.getMetadata().getName());
    }

    @Override
    @Nullable
    public ResourceCrd get(String slug, String namespace) {
        log.warn("Kubernetes client not available — cannot fetch CRD {} in namespace {}", slug, namespace);
        return null;
    }
}

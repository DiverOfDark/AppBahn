package eu.appbahn.platform.resource.service;

import eu.appbahn.shared.crd.ResourceCrd;
import org.springframework.lang.Nullable;

/** Abstraction over Kubernetes operations on Resource CRDs. */
public interface ResourceCrdClient {

    /** Create a new Resource CRD. */
    void create(ResourceCrd crd);

    /** Update an existing Resource CRD. */
    void update(ResourceCrd crd);

    /** Delete a Resource CRD. */
    void delete(ResourceCrd crd);

    /** Get a Resource CRD by slug and namespace. Returns null if not found. */
    @Nullable
    ResourceCrd get(String slug, String namespace);
}

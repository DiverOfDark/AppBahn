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

    /**
     * Delete a Resource CRD by identity (slug + namespace). Tolerates the object being absent.
     * Prefer this over {@link #delete(ResourceCrd)} when the caller only has the identity and
     * does not need to pre-fetch the CRD (which would be racy and add unnecessary I/O to a JPA
     * transaction).
     */
    void delete(String slug, String namespace);

    /** Get a Resource CRD by slug and namespace. Returns null if not found. */
    @Nullable
    ResourceCrd get(String slug, String namespace);
}

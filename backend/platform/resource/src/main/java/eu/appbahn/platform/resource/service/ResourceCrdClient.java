package eu.appbahn.platform.resource.service;

import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import org.springframework.lang.Nullable;

/**
 * Abstraction over Kubernetes operations on Resource + ImageSource CRD pairs.
 * The pair is the platform's atomic unit — a Resource cannot exist without its sibling ImageSource.
 */
public interface ResourceCrdClient {

    /** Create or update a Resource + ImageSource pair atomically. */
    void applyBundle(ResourceCrd resource, ImageSourceCrd imageSource);

    /**
     * Update an existing Resource and (optionally) its sibling ImageSource. The {@code imageSource}
     * may be null when only the Resource is changing (e.g. lifecycle stop/start, restart bump).
     */
    void update(ResourceCrd resource, @Nullable ImageSourceCrd imageSource);

    /**
     * Delete a Resource CRD by identity (slug + namespace). Tolerates the object being absent.
     * The sibling ImageSource cascade-deletes via OwnerReference.
     */
    void delete(String slug, String namespace);

    /** Get a Resource CRD by slug and namespace. Returns null if not found. */
    @Nullable
    ResourceCrd get(String slug, String namespace);
}

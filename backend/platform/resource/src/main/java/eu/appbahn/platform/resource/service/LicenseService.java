package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.common.exception.LicenseLimitException;
import eu.appbahn.platform.resource.repository.ResourceCacheRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces community-tier resource limits. All resources (including stopped) count against the
 * limit. The limit is platform-wide, not per-environment.
 *
 * <p>A global advisory lock is acquired within the enclosing transaction so that concurrent create
 * requests are serialised at the license-check level. The lock key is derived from
 * hashtext('appbahn_license_check') to avoid collisions with other advisory lock users.
 */
@Service
public class LicenseService {

    private final ResourceCacheRepository resourceCacheRepository;
    private final EntityManager entityManager;
    private final int resourceLimit;

    public LicenseService(
            ResourceCacheRepository resourceCacheRepository,
            EntityManager entityManager,
            @Value("${platform.license.resource-limit:5}") int resourceLimit) {
        this.resourceCacheRepository = resourceCacheRepository;
        this.entityManager = entityManager;
        this.resourceLimit = resourceLimit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void checkLicense() {
        if (resourceLimit <= 0) {
            return; // Unlimited
        }
        // Acquire a global advisory lock so concurrent creates are serialised
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(hashtext('appbahn_license_check'))")
                .getSingleResult();

        long count = resourceCacheRepository.count();
        if (count >= resourceLimit) {
            throw new LicenseLimitException((int) count, resourceLimit);
        }
    }
}

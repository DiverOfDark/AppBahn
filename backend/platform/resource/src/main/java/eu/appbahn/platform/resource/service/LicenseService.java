package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.common.exception.LicenseLimitException;
import eu.appbahn.platform.resource.license.LicenseClaims;
import eu.appbahn.platform.resource.repository.ResourceCacheRepository;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces the resource cap. The cap comes from a verified {@link LicenseClaims} when the
 * platform was started with {@code platform.license.file=...}; otherwise the platform runs in
 * community mode with a hardcoded cap of {@value #COMMUNITY_RESOURCE_LIMIT}.
 *
 * <p>All resources (including stopped) count against the limit. The limit is platform-wide, not
 * per-environment. A global PostgreSQL advisory lock is acquired within the enclosing transaction
 * so concurrent create requests are serialised at the licence-check level. The lock key is
 * derived from {@code hashtext('appbahn_license_check')} to avoid collisions with other advisory
 * lock users.
 */
public class LicenseService {

    /** Hard-coded community-mode cap. Cannot be overridden — lift the cap by issuing a license. */
    public static final int COMMUNITY_RESOURCE_LIMIT = 5;

    private final ResourceCacheRepository resourceCacheRepository;
    private final EntityManager entityManager;
    private final int resourceLimit;

    public LicenseService(
            ResourceCacheRepository resourceCacheRepository, EntityManager entityManager, LicenseClaims claims) {
        this.resourceCacheRepository = resourceCacheRepository;
        this.entityManager = entityManager;
        this.resourceLimit = claims == null ? COMMUNITY_RESOURCE_LIMIT : claims.maxResources();
    }

    /** Effective resource cap. Visible for tests; production code only checks via {@link #checkLicense()}. */
    public int getResourceLimit() {
        return resourceLimit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void checkLicense() {
        // Global advisory lock — serialise concurrent creates so the count stays honest.
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(hashtext('appbahn_license_check'))")
                .getSingleResult();

        long count = resourceCacheRepository.count();
        if (count >= resourceLimit) {
            throw new LicenseLimitException((int) count, resourceLimit);
        }
    }
}

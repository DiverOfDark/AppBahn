package eu.appbahn.platform.resource.license;

import eu.appbahn.platform.resource.repository.ResourceCacheRepository;
import eu.appbahn.platform.resource.service.LicenseService;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the license loader into Spring. Read the file path from {@code platform.license.file}
 * (env: {@code PLATFORM_LICENSE_FILE}). Absent ⇒ community mode (max
 * {@value LicenseService#COMMUNITY_RESOURCE_LIMIT} resources). Present but unreadable / invalid /
 * expired ⇒ {@link LicenseValidationException} propagates and Spring fails the boot.
 */
@Configuration
public class LicenseConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LicenseConfiguration.class);

    @Bean
    public LicenseLoader licenseLoader() {
        return LicenseLoader.defaultLoader();
    }

    @Bean
    public LicenseService licenseService(
            ResourceCacheRepository resourceCacheRepository,
            EntityManager entityManager,
            LicenseLoader loader,
            @Value("${platform.license.file:}") String licenseFilePath) {
        LicenseClaims claims = loadClaims(loader, licenseFilePath);
        return new LicenseService(resourceCacheRepository, entityManager, claims);
    }

    private LicenseClaims loadClaims(LicenseLoader loader, String licenseFilePath) {
        if (licenseFilePath == null || licenseFilePath.isBlank()) {
            log.info(
                    "No license file configured; running in community mode (max {} resources)",
                    LicenseService.COMMUNITY_RESOURCE_LIMIT);
            return null;
        }
        LicenseClaims claims = loader.load(Path.of(licenseFilePath));
        log.info(
                "License loaded: customer={}, maxResources={}, expires={}",
                claims.customerId(),
                claims.maxResources(),
                DateTimeFormatter.ISO_INSTANT.format(claims.expiresAt()));
        return claims;
    }
}

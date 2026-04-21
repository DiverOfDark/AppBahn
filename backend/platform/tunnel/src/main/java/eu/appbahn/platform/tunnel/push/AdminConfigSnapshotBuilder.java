package eu.appbahn.platform.tunnel.push;

import eu.appbahn.tunnel.v1.AdminConfigPush;
import eu.appbahn.tunnel.v1.AdminConfigSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Produces an {@link AdminConfigPush} frame carrying platform-wide settings the operator
 * needs (base domain, registry coordinates, namespace prefix). Currently sourced from the
 * platform's {@code application.yml}; an admin-config CRUD UI would feed this builder
 * later. Revision is content-addressed: same snapshot bytes → same revision on any
 * replica.
 */
@Service
public class AdminConfigSnapshotBuilder {

    private final String baseDomain;
    private final String registryUrl;
    private final String registryRepositoryPrefix;
    private final String namespacePrefix;

    public AdminConfigSnapshotBuilder(
            @Value("${platform.domain.base:appbahn.local}") String baseDomain,
            @Value("${platform.registry.url:}") String registryUrl,
            @Value("${platform.registry.repository-prefix:}") String registryRepositoryPrefix,
            @Value("${platform.namespace-prefix:abp}") String namespacePrefix) {
        this.baseDomain = baseDomain;
        this.registryUrl = registryUrl;
        this.registryRepositoryPrefix = registryRepositoryPrefix;
        this.namespacePrefix = namespacePrefix;
    }

    public AdminConfigPush build() {
        AdminConfigSnapshot snapshot = AdminConfigSnapshot.newBuilder()
                .setBaseDomain(baseDomain)
                .setRegistryUrl(registryUrl)
                .setRegistryRepositoryPrefix(registryRepositoryPrefix)
                .setNamespacePrefix(namespacePrefix)
                .build();
        return AdminConfigPush.newBuilder()
                .setRevision(SnapshotRevisions.contentRevision(snapshot))
                .setSnapshot(snapshot)
                .build();
    }
}

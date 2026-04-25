package eu.appbahn.platform.tunnel.push;

import eu.appbahn.platform.api.tunnel.AdminConfigPush;
import eu.appbahn.platform.api.tunnel.AdminConfigSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Produces an {@link AdminConfigPush} carrying platform-wide settings the operator needs
 * (base domain, registry coordinates, namespace prefix). Currently sourced from the
 * platform's {@code application.yml}; an admin-config CRUD UI would feed this builder later.
 * Revision is content-addressed — same snapshot → same revision on any replica.
 */
@Service
public class AdminConfigSnapshotBuilder {

    private final String baseDomain;
    private final String registryUrl;
    private final String registryRepositoryPrefix;
    private final String namespacePrefix;
    private final SnapshotRevisions revisions;

    public AdminConfigSnapshotBuilder(
            @Value("${platform.domain.base:appbahn.local}") String baseDomain,
            @Value("${platform.registry.url:}") String registryUrl,
            @Value("${platform.registry.repository-prefix:}") String registryRepositoryPrefix,
            @Value("${platform.namespace-prefix:abp}") String namespacePrefix,
            SnapshotRevisions revisions) {
        this.baseDomain = baseDomain;
        this.registryUrl = registryUrl;
        this.registryRepositoryPrefix = registryRepositoryPrefix;
        this.namespacePrefix = namespacePrefix;
        this.revisions = revisions;
    }

    public AdminConfigPush build() {
        var snapshot = new AdminConfigSnapshot();
        snapshot.setBaseDomain(baseDomain);
        snapshot.setRegistryUrl(registryUrl);
        snapshot.setRegistryRepositoryPrefix(registryRepositoryPrefix);
        snapshot.setNamespacePrefix(namespacePrefix);
        var push = new AdminConfigPush();
        push.setSnapshot(snapshot);
        push.setRevision(revisions.contentRevision(snapshot));
        return push;
    }
}

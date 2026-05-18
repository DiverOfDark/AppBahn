package eu.appbahn.platform.tunnel.push;

import eu.appbahn.platform.api.tunnel.AdminConfigPush;
import eu.appbahn.platform.api.tunnel.AdminConfigSnapshot;
import eu.appbahn.platform.tunnel.cluster.ClusterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Produces an {@link AdminConfigPush} carrying platform-wide settings the operator needs
 * (base domain, registry coordinates, namespace prefix) plus the receiving cluster's
 * {@code nodePools} catalogue. Platform-wide values are sourced from the platform's
 * {@code application.yml}; per-cluster values come from {@code cluster.config}. Revision is
 * content-addressed — same snapshot → same revision on any replica.
 */
@Service
public class AdminConfigSnapshotBuilder {

    private final String baseDomain;
    private final String registryUrl;
    private final String registryRepositoryPrefix;
    private final String namespacePrefix;
    private final SnapshotRevisions revisions;
    private final ClusterRepository clusters;

    public AdminConfigSnapshotBuilder(
            @Value("${platform.base-domain:appbahn.local}") String baseDomain,
            @Value("${platform.registry.url:}") String registryUrl,
            @Value("${platform.registry.repository-prefix:}") String registryRepositoryPrefix,
            @Value("${platform.namespace-prefix:abp}") String namespacePrefix,
            SnapshotRevisions revisions,
            ClusterRepository clusters) {
        this.baseDomain = baseDomain;
        this.registryUrl = registryUrl;
        this.registryRepositoryPrefix = registryRepositoryPrefix;
        this.namespacePrefix = namespacePrefix;
        this.revisions = revisions;
        this.clusters = clusters;
    }

    public AdminConfigPush buildFor(String clusterName) {
        var snapshot = new AdminConfigSnapshot();
        snapshot.setBaseDomain(baseDomain);
        snapshot.setRegistryUrl(registryUrl);
        snapshot.setRegistryRepositoryPrefix(registryRepositoryPrefix);
        snapshot.setNamespacePrefix(namespacePrefix);
        clusters.findById(clusterName)
                .map(c -> c.getConfig())
                .ifPresent(config -> snapshot.setNodePools(config.getNodePools()));
        var push = new AdminConfigPush();
        push.setSnapshot(snapshot);
        push.setRevision(revisions.contentRevision(snapshot));
        return push;
    }
}

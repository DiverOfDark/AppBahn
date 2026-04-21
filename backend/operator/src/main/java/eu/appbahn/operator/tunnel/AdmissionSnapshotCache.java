package eu.appbahn.operator.tunnel;

import eu.appbahn.tunnel.v1.QuotaRbacSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Caches the latest {@link QuotaRbacSnapshot} the platform pushed. The admission webhook
 * consults this for fail-closed allow/deny decisions: unknown namespace, user not authorised
 * on the env, env resource-count quota exceeded. Revision is content-addressed (SHA-256
 * prefix of the snapshot bytes), so a push with the same revision is a no-op; any different
 * revision is accepted — ordering is given by the stream itself.
 */
@Service
public class AdmissionSnapshotCache {

    private static final Logger log = LoggerFactory.getLogger(AdmissionSnapshotCache.class);

    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    public void ingest(long revision, QuotaRbacSnapshot snapshot) {
        Snapshot existing = current.get();
        if (existing != null && existing.revision() == revision) {
            log.debug("QuotaRbacCachePush revision {} matches current — ignoring duplicate", revision);
            return;
        }
        current.set(new Snapshot(revision, snapshot));
        log.info("Updated admission snapshot: revision={}, envs={}", revision, snapshot.getEnvironmentsCount());
    }

    public boolean hasSnapshot() {
        return current.get() != null;
    }

    /** The currently-cached revision, or {@code 0} if no snapshot has been ingested yet. */
    public long revision() {
        Snapshot held = current.get();
        return held == null ? 0L : held.revision();
    }

    public Optional<String> environmentSlugForNamespace(String namespace) {
        return entryForNamespace(namespace).map(QuotaRbacSnapshot.EnvironmentEntry::getSlug);
    }

    public Optional<QuotaRbacSnapshot.EnvironmentEntry> entryForNamespace(String namespace) {
        Snapshot snapshot = current.get();
        if (snapshot == null || namespace == null) {
            return Optional.empty();
        }
        return snapshot.payload().getEnvironmentsList().stream()
                .filter(e -> namespace.equals(e.getNamespace()))
                .findFirst();
    }

    public List<String> platformAdminGroups() {
        Snapshot snapshot = current.get();
        return snapshot == null ? List.of() : snapshot.payload().getPlatformAdminGroupsList();
    }

    private record Snapshot(long revision, QuotaRbacSnapshot payload) {}
}

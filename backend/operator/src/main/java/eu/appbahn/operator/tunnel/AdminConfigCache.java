package eu.appbahn.operator.tunnel;

import eu.appbahn.tunnel.v1.AdminConfigSnapshot;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Caches the latest {@link AdminConfigSnapshot} the platform pushed (base domain, registry
 * coordinates, namespace prefix). Revision is content-addressed — duplicate revisions are
 * ignored, any different revision is accepted (stream ordering is the authority).
 */
@Service
public class AdminConfigCache {

    private static final Logger log = LoggerFactory.getLogger(AdminConfigCache.class);

    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    public void ingest(long revision, AdminConfigSnapshot snapshot) {
        Snapshot existing = current.get();
        if (existing != null && existing.revision() == revision) {
            log.debug("AdminConfigPush revision {} matches current — ignoring duplicate", revision);
            return;
        }
        current.set(new Snapshot(revision, snapshot));
        log.info(
                "Updated admin-config snapshot: revision={}, baseDomain={}, namespacePrefix={}",
                revision,
                snapshot.getBaseDomain(),
                snapshot.getNamespacePrefix());
    }

    public Optional<AdminConfigSnapshot> snapshot() {
        Snapshot held = current.get();
        return held == null ? Optional.empty() : Optional.of(held.payload());
    }

    /** The currently-cached revision, or {@code 0} if no snapshot has been ingested yet. */
    public long revision() {
        Snapshot held = current.get();
        return held == null ? 0L : held.revision();
    }

    private record Snapshot(long revision, AdminConfigSnapshot payload) {}
}

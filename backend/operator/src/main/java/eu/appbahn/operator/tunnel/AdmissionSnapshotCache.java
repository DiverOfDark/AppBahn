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
 * on the env, per-dimension quota exceeded at env/project/workspace scope. Revision is
 * content-addressed (SHA-256 prefix of the snapshot bytes), so a push with the same revision
 * is a no-op; any different revision is accepted — ordering is given by the stream itself.
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
        log.info(
                "Updated admission snapshot: revision={}, envs={}, projects={}, workspaces={}",
                revision,
                snapshot.getEnvironmentsCount(),
                snapshot.getProjectsCount(),
                snapshot.getWorkspacesCount());
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

    public Optional<QuotaRbacSnapshot.ProjectEntry> projectEntry(String projectSlug) {
        Snapshot snapshot = current.get();
        if (snapshot == null || projectSlug == null || projectSlug.isEmpty()) {
            return Optional.empty();
        }
        return snapshot.payload().getProjectsList().stream()
                .filter(p -> projectSlug.equals(p.getSlug()))
                .findFirst();
    }

    public Optional<QuotaRbacSnapshot.WorkspaceEntry> workspaceEntry(String workspaceSlug) {
        Snapshot snapshot = current.get();
        if (snapshot == null || workspaceSlug == null || workspaceSlug.isEmpty()) {
            return Optional.empty();
        }
        return snapshot.payload().getWorkspacesList().stream()
                .filter(w -> workspaceSlug.equals(w.getSlug()))
                .findFirst();
    }

    /** All env entries whose {@code project_slug} matches — used for per-project rollup sums. */
    public List<QuotaRbacSnapshot.EnvironmentEntry> envEntriesInProject(String projectSlug) {
        Snapshot snapshot = current.get();
        if (snapshot == null || projectSlug == null || projectSlug.isEmpty()) {
            return List.of();
        }
        return snapshot.payload().getEnvironmentsList().stream()
                .filter(e -> projectSlug.equals(e.getProjectSlug()))
                .toList();
    }

    /** All env entries whose {@code workspace_slug} matches — used for per-workspace rollup sums. */
    public List<QuotaRbacSnapshot.EnvironmentEntry> envEntriesInWorkspace(String workspaceSlug) {
        Snapshot snapshot = current.get();
        if (snapshot == null || workspaceSlug == null || workspaceSlug.isEmpty()) {
            return List.of();
        }
        return snapshot.payload().getEnvironmentsList().stream()
                .filter(e -> workspaceSlug.equals(e.getWorkspaceSlug()))
                .toList();
    }

    public List<String> platformAdminGroups() {
        Snapshot snapshot = current.get();
        return snapshot == null ? List.of() : snapshot.payload().getPlatformAdminGroupsList();
    }

    private record Snapshot(long revision, QuotaRbacSnapshot payload) {}
}

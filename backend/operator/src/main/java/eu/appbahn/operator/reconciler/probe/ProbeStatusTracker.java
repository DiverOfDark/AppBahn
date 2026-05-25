package eu.appbahn.operator.reconciler.probe;

import eu.appbahn.shared.crd.ResourceStatusDetail.ProbeOutcome;
import eu.appbahn.shared.crd.ResourceStatusDetail.ProbeStatusBlock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * In-memory store of the latest {@link ProbeOutcome} per (resource, probe type). The
 * operator-side periodic probe runner and the kubelet event watcher both write here; the
 * {@code ResourceReconciler} reads here when assembling the status subresource.
 *
 * <p>Writes are last-write-wins by wall-clock timestamp — the kubelet event and the operator's own
 * probe run typically land within seconds of each other for a failing pod, and either ordering
 * produces a sensible "ok=false, lastCheckedAt=<recent>" record.
 *
 * <p>Coalescing: this class only holds the latest outcome. The reconciler tick is what writes to
 * etcd; updates between ticks accumulate into a single etcd write per reconcile cycle (driven by
 * the {@code probeStatusChangeNotifier} reschedule, which is debounced).
 */
@Component
public class ProbeStatusTracker {

    private final ConcurrentMap<ResourceKey, ConcurrentMap<ProbeType, ProbeOutcome>> outcomes =
            new ConcurrentHashMap<>();

    /** Identifier for a Resource CRD — namespace + name (the operator never sees cross-cluster). */
    public record ResourceKey(String namespace, String name) {

        public static ResourceKey of(String namespace, String name) {
            return new ResourceKey(namespace, name);
        }
    }

    /**
     * Record a fresh outcome. Returns {@code true} if the new outcome is materially different from
     * the previous one (status flip or first observation) — callers use this to decide whether to
     * trigger a reconcile.
     *
     * <p>"Material change" intentionally ignores tiny latency wiggle (a 12ms→13ms jitter shouldn't
     * spam etcd) — only an {@code ok} flip or a first observation counts.
     */
    public boolean record(ResourceKey key, ProbeType type, ProbeOutcome outcome) {
        ConcurrentMap<ProbeType, ProbeOutcome> perResource =
                outcomes.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        ProbeOutcome previous = perResource.put(type, outcome);
        if (previous == null) {
            return true;
        }
        return !equalsOk(previous, outcome);
    }

    /** Snapshot of the current probe status for a resource, or {@code null} if nothing recorded yet. */
    public ProbeStatusBlock snapshot(ResourceKey key) {
        Map<ProbeType, ProbeOutcome> perResource = outcomes.get(key);
        if (perResource == null || perResource.isEmpty()) {
            return null;
        }
        ProbeStatusBlock block = new ProbeStatusBlock();
        block.setLiveness(perResource.get(ProbeType.LIVENESS));
        block.setReadiness(perResource.get(ProbeType.READINESS));
        block.setStartup(perResource.get(ProbeType.STARTUP));
        if (block.getLiveness() == null && block.getReadiness() == null && block.getStartup() == null) {
            return null;
        }
        return block;
    }

    /** Drop everything we have for a resource — used when the CR is deleted. */
    public void forget(ResourceKey key) {
        outcomes.remove(key);
    }

    /** Test/debug accessor: the live per-resource map (immutable view of present keys). */
    public Map<ResourceKey, Map<ProbeType, ProbeOutcome>> snapshotAll() {
        Map<ResourceKey, Map<ProbeType, ProbeOutcome>> result = new HashMap<>();
        outcomes.forEach((k, v) -> result.put(k, Map.copyOf(v)));
        return result;
    }

    private static boolean equalsOk(ProbeOutcome a, ProbeOutcome b) {
        return java.util.Objects.equals(a.getOk(), b.getOk());
    }
}

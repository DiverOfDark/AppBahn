package eu.appbahn.operator.reconciler.probe;

import eu.appbahn.operator.reconciler.probe.ProbeStatusTracker.ResourceKey;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.ResourceStatusDetail;
import eu.appbahn.shared.crd.ResourceStatusDetail.ProbeStatusBlock;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Writes a batch of {@link ProbeStatusBlock} updates to the Resource status subresource. One etcd
 * call per resource — the {@link OperatorProbeRunner} hands us the snapshot collected during its
 * tick, and we patch only resources whose probe-status actually changed since the last write.
 */
@Component
public class ProbeStatusWriter {

    private static final Logger log = LoggerFactory.getLogger(ProbeStatusWriter.class);

    private final KubernetesClient client;

    public ProbeStatusWriter(KubernetesClient client) {
        this.client = client;
    }

    /** Push every entry in {@code batch} to etcd. Same-shape writes are skipped. */
    public void flush(Map<ResourceKey, ProbeStatusBlock> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        for (var entry : batch.entrySet()) {
            ResourceKey key = entry.getKey();
            ProbeStatusBlock fresh = entry.getValue();
            try {
                patch(key, fresh);
            } catch (Exception e) {
                log.debug("Failed to patch probeStatus for {}/{}: {}", key.namespace(), key.name(), e.getMessage());
            }
        }
    }

    private void patch(ResourceKey key, ProbeStatusBlock fresh) {
        var handle =
                client.resources(ResourceCrd.class).inNamespace(key.namespace()).withName(key.name());
        ResourceCrd current = handle.get();
        if (current == null) {
            return;
        }
        ResourceStatusDetail status = current.getStatus();
        if (status == null) {
            // No status yet — let the main reconciler create one. Without an existing status the
            // editStatus path would need to materialize the whole shape, which is the reconciler's
            // responsibility.
            return;
        }
        if (Objects.equals(status.getProbeStatus(), fresh)) {
            return;
        }
        try {
            handle.editStatus(existing -> {
                if (existing.getStatus() == null) {
                    existing.setStatus(new ResourceStatusDetail());
                }
                existing.getStatus().setProbeStatus(fresh);
                return existing;
            });
        } catch (Exception e) {
            log.debug("editStatus failed for {}/{}: {}", key.namespace(), key.name(), e.getMessage());
        }
    }
}

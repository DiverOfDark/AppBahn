package eu.appbahn.operator.reconciler.probe;

import eu.appbahn.operator.reconciler.probe.ProbeStatusTracker.ResourceKey;
import eu.appbahn.shared.K8sStatusReasons;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.ResourceStatusDetail.ProbeOutcome;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Watches core/v1 Events cluster-wide for kubelet probe failures. Each {@code reason=Unhealthy}
 * event whose message starts with "Liveness probe failed" / "Readiness probe failed" /
 * "Startup probe failed" is parsed and recorded as a failing {@link ProbeOutcome} on the owning
 * Resource.
 *
 * <p>Kubelet does not emit events on successful probes — the {@link OperatorProbeRunner} fills
 * that gap. Kubelet event payloads do not include probe duration, so latency is left {@code null}
 * here.
 */
@Component
public class KubeletProbeEventWatcher implements Watcher<Event> {

    private static final Logger log = LoggerFactory.getLogger(KubeletProbeEventWatcher.class);

    private static final String POD_KIND = "Pod";

    private final KubernetesClient client;
    private final ProbeStatusTracker tracker;

    private Watch watch;

    public KubeletProbeEventWatcher(KubernetesClient client, ProbeStatusTracker tracker) {
        this.client = client;
        this.tracker = tracker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (watch != null) {
            return;
        }
        log.info("Starting kubelet probe event watch (cluster-wide)");
        watch = client.v1().events().inAnyNamespace().watch(this);
    }

    @PostConstruct
    void postConstruct() {
        // Defer actual watch start to ApplicationReadyEvent — the JOSDK informers and the
        // KubernetesClient are both wired by then. Keep this method around so Spring's lifecycle
        // shows the bean exists even before the app is ready.
    }

    @PreDestroy
    public void stop() {
        if (watch != null) {
            try {
                watch.close();
            } catch (Exception e) {
                log.debug("Error closing probe event watch: {}", e.getMessage());
            }
            watch = null;
        }
    }

    @Override
    public void eventReceived(Action action, Event event) {
        if (action == Action.DELETED || action == Action.ERROR) {
            return;
        }
        if (event == null || event.getReason() == null || !K8sStatusReasons.UNHEALTHY.equals(event.getReason())) {
            return;
        }
        var involved = event.getInvolvedObject();
        if (involved == null || !POD_KIND.equals(involved.getKind())) {
            return;
        }
        ProbeType probeType = ProbeType.fromKubeletMessage(event.getMessage());
        if (probeType == null) {
            // Unhealthy event with a message we don't recognize (sidecar probe, ingress controller,
            // etc.) — ignore.
            return;
        }
        ResourceKey resourceKey = resolveResource(involved.getNamespace(), involved.getName());
        if (resourceKey == null) {
            return;
        }

        ProbeOutcome outcome = new ProbeOutcome();
        outcome.setOk(false);
        outcome.setLastCheckedAt(eventTime(event));
        // latency is intentionally null — kubelet doesn't include it

        tracker.record(resourceKey, probeType, outcome);
        // The periodic OperatorProbeRunner is the sole writer to etcd; tick lag is bounded by
        // its 60s cadence. Triggering a reconcile here would compete with that single writer for
        // limited gain (UI refresh latency drops from ~30s p50 to ~5s) at the cost of doubled
        // status patches under chronic-failure conditions.
    }

    @Override
    public void onClose(WatcherException cause) {
        if (cause != null) {
            log.warn("Probe event watch closed unexpectedly: {}", cause.getMessage());
            // The KubernetesClient handles reconnection for its long-polling watches automatically;
            // if this fires we still log so a flapping watch is visible in operator logs.
        }
    }

    /**
     * Resolve the owning Resource CR for a pod by reading its labels. Looks up the pod from the
     * apiserver — the watch only carries the {@code Event}, not the involved object.
     */
    private ResourceKey resolveResource(String namespace, String podName) {
        if (namespace == null || podName == null) {
            return null;
        }
        try {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null || pod.getMetadata() == null || pod.getMetadata().getLabels() == null) {
                return null;
            }
            String resourceName = pod.getMetadata().getLabels().get(Labels.RESOURCE_KEY);
            if (resourceName == null) {
                return null;
            }
            // Sanity-check the Resource exists — protects against label leakage.
            ResourceCrd resource = client.resources(ResourceCrd.class)
                    .inNamespace(namespace)
                    .withName(resourceName)
                    .get();
            if (resource == null) {
                return null;
            }
            return ResourceKey.of(namespace, resourceName);
        } catch (Exception e) {
            log.debug("Failed to resolve owning Resource for pod {}/{}: {}", namespace, podName, e.getMessage());
            return null;
        }
    }

    private static Instant eventTime(Event event) {
        if (event.getEventTime() != null && event.getEventTime().getTime() != null) {
            try {
                return Instant.parse(event.getEventTime().getTime());
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (event.getLastTimestamp() != null) {
            try {
                return Instant.parse(event.getLastTimestamp());
            } catch (Exception ignored) {
                // fall through
            }
        }
        return Instant.now();
    }
}

package eu.appbahn.operator.reconciler.probe;

import eu.appbahn.operator.tunnel.OperatorEventPublisher;
import eu.appbahn.operator.tunnel.client.model.ResourceK8sEvent;
import eu.appbahn.shared.K8sEventReasons;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Watches core/v1 Events cluster-wide and forwards those whose {@code reason} is in
 * {@link K8sEventReasons#SURFACED} and whose involved object is owned by a Resource. The owning
 * Resource slug is resolved from the {@code appbahn.eu/resource} label on the involved object (Pod)
 * or its parent ReplicaSet. Matches are emitted to the platform as {@link ResourceK8sEvent}s, which
 * the platform buffers and replays onto open {@code /logs/stream} SSE connections.
 *
 * <p>This is the live-feed sibling of {@link KubeletProbeEventWatcher}: that watcher records probe
 * outcomes onto Resource status; this one forwards the broader surfaced-reason set for the console's
 * live event stream. The two share the same single cluster-wide Event watch contract but write to
 * different sinks.
 */
@Component
public class ResourceEventWatcher implements Watcher<Event> {

    private static final Logger log = LoggerFactory.getLogger(ResourceEventWatcher.class);

    private static final String POD_KIND = "Pod";
    private static final String REPLICA_SET_KIND = "ReplicaSet";

    private final KubernetesClient client;
    private final OperatorEventPublisher eventPublisher;

    private Watch watch;

    public ResourceEventWatcher(KubernetesClient client, OperatorEventPublisher eventPublisher) {
        this.client = client;
        this.eventPublisher = eventPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (watch != null) {
            return;
        }
        log.info("Starting Resource event watch (cluster-wide)");
        watch = client.v1().events().inAnyNamespace().watch(this);
    }

    @PreDestroy
    public void stop() {
        if (watch != null) {
            try {
                watch.close();
            } catch (Exception e) {
                log.debug("Error closing Resource event watch: {}", e.getMessage());
            }
            watch = null;
        }
    }

    @Override
    public void eventReceived(Action action, Event event) {
        if (action == Action.DELETED || action == Action.ERROR) {
            return;
        }
        if (event == null || event.getReason() == null || !K8sEventReasons.SURFACED.contains(event.getReason())) {
            return;
        }
        ObjectReference involved = event.getInvolvedObject();
        if (involved == null || involved.getNamespace() == null || involved.getName() == null) {
            return;
        }
        String resourceSlug = resolveResourceSlug(involved);
        if (resourceSlug == null) {
            return;
        }

        var k8sEvent = new ResourceK8sEvent();
        k8sEvent.setNamespace(involved.getNamespace());
        k8sEvent.setResourceSlug(resourceSlug);
        k8sEvent.setEventType(event.getType() != null ? event.getType() : K8sEventReasons.NORMAL);
        k8sEvent.setReason(event.getReason());
        k8sEvent.setMessage(event.getMessage());
        k8sEvent.setInvolvedKind(involved.getKind());
        k8sEvent.setInvolvedName(involved.getName());
        k8sEvent.setPod(POD_KIND.equals(involved.getKind()) ? involved.getName() : null);
        k8sEvent.setCount(event.getCount() != null ? event.getCount() : 1);
        k8sEvent.setEventTime(eventTime(event));
        try {
            eventPublisher.emit(k8sEvent);
        } catch (Exception e) {
            log.debug("Failed to enqueue ResourceK8sEvent for {}: {}", resourceSlug, e.getMessage());
        }
    }

    @Override
    public void onClose(WatcherException cause) {
        if (cause != null) {
            log.warn("Resource event watch closed unexpectedly: {}", cause.getMessage());
        }
    }

    /**
     * Resolve the owning Resource slug for the involved object. Pods carry the
     * {@code appbahn.eu/resource} label directly; ReplicaSet events resolve through the RS's own
     * label. Returns null for objects not owned by a Resource (sanity-checked against the
     * Resource CR's existence to guard against label leakage).
     */
    private String resolveResourceSlug(ObjectReference involved) {
        try {
            String slug =
                    switch (involved.getKind()) {
                        case POD_KIND -> labelOf(podLabels(involved.getNamespace(), involved.getName()));
                        case REPLICA_SET_KIND -> labelOf(replicaSetLabels(involved.getNamespace(), involved.getName()));
                        default -> null;
                    };
            if (slug == null) {
                return null;
            }
            ResourceCrd resource = client.resources(ResourceCrd.class)
                    .inNamespace(involved.getNamespace())
                    .withName(slug)
                    .get();
            return resource != null ? slug : null;
        } catch (Exception e) {
            log.debug(
                    "Failed to resolve owning Resource for {}/{} {}: {}",
                    involved.getNamespace(),
                    involved.getKind(),
                    involved.getName(),
                    e.getMessage());
            return null;
        }
    }

    private java.util.Map<String, String> podLabels(String namespace, String name) {
        Pod pod = client.pods().inNamespace(namespace).withName(name).get();
        return pod != null && pod.getMetadata() != null ? pod.getMetadata().getLabels() : null;
    }

    private java.util.Map<String, String> replicaSetLabels(String namespace, String name) {
        ReplicaSet rs = client.apps()
                .replicaSets()
                .inNamespace(namespace)
                .withName(name)
                .get();
        return rs != null && rs.getMetadata() != null ? rs.getMetadata().getLabels() : null;
    }

    private static String labelOf(java.util.Map<String, String> labels) {
        return labels != null ? labels.get(Labels.RESOURCE_KEY) : null;
    }

    private static OffsetDateTime eventTime(Event event) {
        String raw = null;
        if (event.getEventTime() != null && event.getEventTime().getTime() != null) {
            raw = event.getEventTime().getTime();
        } else if (event.getLastTimestamp() != null) {
            raw = event.getLastTimestamp();
        }
        if (raw != null) {
            try {
                return Instant.parse(raw).atOffset(ZoneOffset.UTC);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}

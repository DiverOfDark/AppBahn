package eu.appbahn.operator.reconciler.probe;

import eu.appbahn.operator.reconciler.probe.ProbeStatusTracker.ResourceKey;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourceConfig.HealthCheckConfig;
import eu.appbahn.shared.crd.ResourceConfig.ProbeConfig;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.ResourceStatusDetail.ProbeOutcome;
import eu.appbahn.shared.crd.ResourceStatusDetail.ProbeStatusBlock;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// TODO(sprint-12-metrics-provider): replace direct HTTP/TCP dial with ProbeOutcomeProvider abstraction.
// See spec/SPEC.md §3 (Provider Abstractions, lines 210-245) + spec/sprints/sprint-12.md.

/**
 * Periodically runs each Resource's configured probe(s) against a live pod IP and records the
 * outcome (latency + success) in the {@link ProbeStatusTracker}. After each tick, materializes
 * the tracker's per-resource snapshot onto the Resource's {@code status.probeStatus} via a single
 * etcd patch — coalescing every probe write since the prior tick into one update per resource.
 *
 * <p>Probe semantics mirror the kubelet's: HTTP probe = GET {@code /<path>} with a 2xx-3xx success
 * window; TCP probe = socket connect; exec probe = {@code kubectl exec} (not supported in v1 —
 * leaves a status condition explaining).
 */
@Component
public class OperatorProbeRunner {

    private static final Logger log = LoggerFactory.getLogger(OperatorProbeRunner.class);

    /** Per-probe wall-clock budget — fail fast rather than blocking the whole tick. */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

    private final KubernetesClient client;
    private final ProbeStatusTracker tracker;
    private final ProbeStatusWriter writer;
    private final HttpClient httpClient;

    public OperatorProbeRunner(KubernetesClient client, ProbeStatusTracker tracker, ProbeStatusWriter writer) {
        this.client = client;
        this.tracker = tracker;
        this.writer = writer;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(PROBE_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Scheduled(
            fixedDelayString = "${operator.probe-runner-interval-ms:60000}",
            initialDelayString = "${operator.probe-runner-initial-delay-ms:30000}")
    public void tick() {
        try {
            runOnce();
        } catch (Exception e) {
            log.warn("Probe runner tick failed: {}", e.getMessage());
        }
    }

    /** Visible for tests — single iteration without scheduling. */
    public void runOnce() {
        List<ResourceCrd> resources =
                client.resources(ResourceCrd.class).inAnyNamespace().list().getItems();
        ConcurrentMap<ResourceKey, ProbeStatusBlock> toFlush = new ConcurrentHashMap<>();
        for (ResourceCrd resource : resources) {
            try {
                ProbeStatusBlock block = probeOne(resource);
                if (block != null) {
                    toFlush.put(
                            ResourceKey.of(
                                    resource.getMetadata().getNamespace(),
                                    resource.getMetadata().getName()),
                            block);
                }
            } catch (Exception e) {
                log.debug(
                        "Probe-tick failed for {}/{}: {}",
                        resource.getMetadata().getNamespace(),
                        resource.getMetadata().getName(),
                        e.getMessage());
            }
        }
        writer.flush(toFlush);
    }

    private ProbeStatusBlock probeOne(ResourceCrd resource) {
        ResourceConfig config = resource.getSpec() != null ? resource.getSpec().getConfig() : null;
        HealthCheckConfig healthCheck = config != null ? config.getHealthCheck() : null;
        if (healthCheck == null) {
            return null;
        }
        if (healthCheck.getLiveness() == null
                && healthCheck.getReadiness() == null
                && healthCheck.getStartup() == null) {
            return null;
        }
        Pod pod = pickPod(resource);
        if (pod == null || podIp(pod) == null) {
            // No targetable pod (pending, terminating, no IP). Don't write anything — leave the
            // tracker as-is so the last good outcome lingers until the pod comes back or the
            // kubelet event flips us to ok=false.
            return null;
        }
        ResourceKey key = ResourceKey.of(
                resource.getMetadata().getNamespace(), resource.getMetadata().getName());
        runProbeAndRecord(key, ProbeType.LIVENESS, healthCheck.getLiveness(), pod);
        runProbeAndRecord(key, ProbeType.READINESS, healthCheck.getReadiness(), pod);
        runProbeAndRecord(key, ProbeType.STARTUP, healthCheck.getStartup(), pod);
        return tracker.snapshot(key);
    }

    private void runProbeAndRecord(ResourceKey key, ProbeType type, ProbeConfig probe, Pod pod) {
        if (probe == null) {
            return;
        }
        ProbeOutcome outcome = execute(probe, pod);
        if (outcome != null) {
            tracker.record(key, type, outcome);
        }
    }

    private ProbeOutcome execute(ProbeConfig probe, Pod pod) {
        Instant now = Instant.now();
        String ip = podIp(pod);
        try {
            if (probe.getHttpGet() != null) {
                return httpGet(probe.getHttpGet(), ip, now);
            }
            if (probe.getTcpSocket() != null) {
                return tcpProbe(probe.getTcpSocket(), ip, now);
            }
            if (probe.getExec() != null) {
                // Exec probes need kubectl-exec RBAC against pods/exec. Out of scope for v1 —
                // leave latency null and ok=null so the UI shows "—" rather than a false green/red.
                ProbeOutcome out = new ProbeOutcome();
                out.setLastCheckedAt(now);
                return out;
            }
            return null;
        } catch (Exception e) {
            ProbeOutcome out = new ProbeOutcome();
            out.setOk(false);
            out.setLastLatencyMs(
                    Math.max(0, Duration.between(now, Instant.now()).toMillis()));
            out.setLastCheckedAt(now);
            return out;
        }
    }

    private ProbeOutcome httpGet(ResourceConfig.HttpGetAction http, String ip, Instant now) throws Exception {
        Integer port = http.getPort();
        if (port == null || ip == null) {
            return null;
        }
        String path = http.getPath() == null ? "/" : http.getPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        URI uri = URI.create("http://" + ip + ":" + port + path);
        HttpRequest request =
                HttpRequest.newBuilder(uri).timeout(PROBE_TIMEOUT).GET().build();
        long start = System.nanoTime();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        ProbeOutcome out = new ProbeOutcome();
        out.setOk(response.statusCode() >= 200 && response.statusCode() < 400);
        out.setLastLatencyMs(elapsedMs);
        out.setLastCheckedAt(now);
        return out;
    }

    private ProbeOutcome tcpProbe(ResourceConfig.TcpSocketAction tcp, String ip, Instant now) {
        Integer port = tcp.getPort();
        if (port == null || ip == null) {
            return null;
        }
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), (int) PROBE_TIMEOUT.toMillis());
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            ProbeOutcome out = new ProbeOutcome();
            out.setOk(true);
            out.setLastLatencyMs(elapsedMs);
            out.setLastCheckedAt(now);
            return out;
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            ProbeOutcome out = new ProbeOutcome();
            out.setOk(false);
            out.setLastLatencyMs(elapsedMs);
            out.setLastCheckedAt(now);
            return out;
        }
    }

    /**
     * Pick a single Ready pod to probe. For multi-replica resources we sample one pod per tick
     * rather than every pod — the per-resource status surface only has one slot per probe type, so
     * probing all replicas would just last-write-wins anyway.
     */
    private Pod pickPod(ResourceCrd resource) {
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        List<Pod> pods = client.pods()
                .inNamespace(namespace)
                .withLabel(Labels.RESOURCE_KEY, name)
                .list()
                .getItems();
        Pod ready = null;
        Pod fallback = null;
        for (Pod pod : pods) {
            if (pod.getMetadata() != null && pod.getMetadata().getDeletionTimestamp() != null) {
                continue;
            }
            if (podIp(pod) == null) {
                continue;
            }
            if (fallback == null) {
                fallback = pod;
            }
            if (isReady(pod)) {
                ready = pod;
                break;
            }
        }
        return ready != null ? ready : fallback;
    }

    private static String podIp(Pod pod) {
        if (pod.getStatus() == null) {
            return null;
        }
        String ip = pod.getStatus().getPodIP();
        return (ip != null && !ip.isBlank()) ? ip : null;
    }

    private static boolean isReady(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getConditions() == null) {
            return false;
        }
        return pod.getStatus().getConditions().stream()
                .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
    }

    /** Visible for tests. Returns whether the tracker snapshot is empty. */
    Map<ResourceKey, Map<ProbeType, ProbeOutcome>> trackerSnapshot() {
        return tracker.snapshotAll();
    }

    @SuppressWarnings("unused")
    private static boolean nonNull(Object o) {
        return Objects.nonNull(o);
    }
}

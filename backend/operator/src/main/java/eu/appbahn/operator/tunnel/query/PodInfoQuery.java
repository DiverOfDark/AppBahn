package eu.appbahn.operator.tunnel.query;

import eu.appbahn.operator.tunnel.client.model.ListPodsResultEntry;
import eu.appbahn.shared.Labels;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// TODO(sprint-12-metrics-provider): move metrics-server / kube-apiserver query behind MetricsProvider abstraction.
// See spec/SPEC.md §3 (Provider Abstractions, lines 210-245) + spec/sprints/sprint-12.md.

/**
 * Reads pods backing a Resource (filtered by the {@code appbahn.eu/resource} label) and
 * stitches in live CPU/memory from the metrics API when available. Metrics-server is
 * documented as optional with graceful degradation — when the {@code metrics.k8s.io}
 * endpoint isn't installed, {@link #queryMetrics} swallows the failure and the
 * resulting {@code cpuUsedMillicores}/{@code memoryUsedBytes} stay null.
 */
@Service
public class PodInfoQuery {

    private static final Logger log = LoggerFactory.getLogger(PodInfoQuery.class);

    private final KubernetesClient kubernetesClient;

    public PodInfoQuery(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    public List<ListPodsResultEntry> listPods(String namespace, String resourceSlug) {
        List<Pod> pods = kubernetesClient
                .pods()
                .inNamespace(namespace)
                .withLabel(Labels.RESOURCE_KEY, resourceSlug)
                .list()
                .getItems();

        Map<String, PodMetrics> metricsByName = queryMetrics(namespace);

        List<ListPodsResultEntry> out = new ArrayList<>(pods.size());
        Instant now = Instant.now();
        for (Pod pod : pods) {
            out.add(toResult(pod, metricsByName.get(pod.getMetadata().getName()), now));
        }
        return out;
    }

    private ListPodsResultEntry toResult(Pod pod, PodMetrics metrics, Instant now) {
        var entry = new ListPodsResultEntry();
        entry.setName(pod.getMetadata().getName());
        entry.setStatus(pod.getStatus() != null ? pod.getStatus().getPhase() : null);
        entry.setNode(pod.getSpec() != null ? pod.getSpec().getNodeName() : null);

        OffsetDateTime created = parseTimestamp(pod.getMetadata().getCreationTimestamp());
        if (created != null) {
            long age = Duration.between(created.toInstant(), now).getSeconds();
            entry.setAgeSeconds(Math.max(0L, age));
        }

        long cpuLimit = sumContainerCpuLimitMillis(pod);
        long memLimit = sumContainerMemoryLimitBytes(pod);
        if (cpuLimit > 0) {
            entry.setCpuLimitMillicores(cpuLimit);
        }
        if (memLimit > 0) {
            entry.setMemoryLimitBytes(memLimit);
        }
        if (metrics != null) {
            long cpuUsed = sumMetricsCpu(metrics);
            long memUsed = sumMetricsMemory(metrics);
            if (cpuUsed > 0) {
                entry.setCpuUsedMillicores(cpuUsed);
            }
            if (memUsed > 0) {
                entry.setMemoryUsedBytes(memUsed);
            }
        }
        return entry;
    }

    private Map<String, PodMetrics> queryMetrics(String namespace) {
        try {
            var metricsList =
                    kubernetesClient.top().pods().inNamespace(namespace).metrics();
            Map<String, PodMetrics> byName = new HashMap<>();
            if (metricsList != null && metricsList.getItems() != null) {
                for (PodMetrics m : metricsList.getItems()) {
                    if (m.getMetadata() != null && m.getMetadata().getName() != null) {
                        byName.put(m.getMetadata().getName(), m);
                    }
                }
            }
            return byName;
        } catch (Exception e) {
            log.debug(
                    "metrics-server unavailable in namespace {} ({}); pod usage will be null",
                    namespace,
                    e.getMessage());
            return Map.of();
        }
    }

    private long sumContainerCpuLimitMillis(Pod pod) {
        if (pod.getSpec() == null || pod.getSpec().getContainers() == null) {
            return 0L;
        }
        long total = 0L;
        for (var container : pod.getSpec().getContainers()) {
            if (container.getResources() == null || container.getResources().getLimits() == null) {
                continue;
            }
            Quantity cpu = container.getResources().getLimits().get(Labels.RESOURCE_KEY_CPU);
            if (cpu != null) {
                total += QuantityToMillicores.toMillicores(cpu);
            }
        }
        return total;
    }

    private long sumContainerMemoryLimitBytes(Pod pod) {
        if (pod.getSpec() == null || pod.getSpec().getContainers() == null) {
            return 0L;
        }
        long total = 0L;
        for (var container : pod.getSpec().getContainers()) {
            if (container.getResources() == null || container.getResources().getLimits() == null) {
                continue;
            }
            Quantity mem = container.getResources().getLimits().get(Labels.RESOURCE_KEY_MEMORY);
            if (mem != null) {
                total += QuantityToBytes.toBytes(mem);
            }
        }
        return total;
    }

    private long sumMetricsCpu(PodMetrics metrics) {
        if (metrics.getContainers() == null) {
            return 0L;
        }
        long total = 0L;
        for (ContainerMetrics cm : metrics.getContainers()) {
            if (cm.getUsage() == null) {
                continue;
            }
            Quantity cpu = cm.getUsage().get(Labels.RESOURCE_KEY_CPU);
            if (cpu != null) {
                total += QuantityToMillicores.toMillicores(cpu);
            }
        }
        return total;
    }

    private long sumMetricsMemory(PodMetrics metrics) {
        if (metrics.getContainers() == null) {
            return 0L;
        }
        long total = 0L;
        for (ContainerMetrics cm : metrics.getContainers()) {
            if (cm.getUsage() == null) {
                continue;
            }
            Quantity mem = cm.getUsage().get(Labels.RESOURCE_KEY_MEMORY);
            if (mem != null) {
                total += QuantityToBytes.toBytes(mem);
            }
        }
        return total;
    }

    /** Fabric8 reports pod creationTimestamp as an ISO-8601 string; tolerate parse failures. */
    private static OffsetDateTime parseTimestamp(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }
}

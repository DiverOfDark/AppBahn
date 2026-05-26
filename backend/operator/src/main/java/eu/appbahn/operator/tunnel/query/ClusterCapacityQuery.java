package eu.appbahn.operator.tunnel.query;

import eu.appbahn.operator.tunnel.client.model.ClusterCapacityResult;
import eu.appbahn.shared.Labels;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

// TODO(sprint-12-metrics-provider): move metrics-server / kube-apiserver query behind MetricsProvider abstraction.
// See spec/SPEC.md §3 (Provider Abstractions, lines 210-245) + spec/sprints/sprint-12.md.

/**
 * Walks the cluster's nodes + running pods to compute aggregate CPU/memory headroom for
 * the Scale modal's "cluster has X free" preview. The arithmetic mirrors what the
 * scheduler does: {@code available = sum(node.allocatable) - sum(pod.requests)} across
 * schedulable, non-terminal pods. Cordoned nodes are excluded from both sides.
 *
 * <p>Pods in {@code Succeeded} / {@code Failed} phase release their requests back to the
 * scheduler, so we skip them too. Daemonset-style pods on cordoned nodes are simply
 * invisible — their node isn't in our schedulable set, so neither its allocatable nor the
 * pod's requests are counted.
 */
@Service
public class ClusterCapacityQuery {

    private final KubernetesClient kubernetesClient;

    public ClusterCapacityQuery(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    public ClusterCapacityResult compute() {
        List<Node> nodes = kubernetesClient.nodes().list().getItems();
        Set<String> schedulableNodeNames = new HashSet<>();
        long totalCpuMillis = 0L;
        long totalMemBytes = 0L;
        for (Node node : nodes) {
            if (isUnschedulable(node)) {
                continue;
            }
            schedulableNodeNames.add(node.getMetadata().getName());
            if (node.getStatus() == null || node.getStatus().getAllocatable() == null) {
                continue;
            }
            var allocatable = node.getStatus().getAllocatable();
            Quantity cpu = allocatable.get(Labels.RESOURCE_KEY_CPU);
            Quantity mem = allocatable.get(Labels.RESOURCE_KEY_MEMORY);
            if (cpu != null) {
                totalCpuMillis += QuantityToMillicores.toMillicores(cpu);
            }
            if (mem != null) {
                totalMemBytes += QuantityToBytes.toBytes(mem);
            }
        }

        long requestedCpuMillis = 0L;
        long requestedMemBytes = 0L;
        if (!schedulableNodeNames.isEmpty()) {
            List<Pod> allPods = kubernetesClient.pods().inAnyNamespace().list().getItems();
            for (Pod pod : allPods) {
                if (!consumesScheduledCapacity(pod, schedulableNodeNames)) {
                    continue;
                }
                requestedCpuMillis += sumPodRequestsCpuMillis(pod);
                requestedMemBytes += sumPodRequestsMemoryBytes(pod);
            }
        }

        var out = new ClusterCapacityResult();
        out.setCpuTotalMillicores(totalCpuMillis);
        out.setMemoryTotalBytes(totalMemBytes);
        out.setCpuAvailableMillicores(Math.max(0L, totalCpuMillis - requestedCpuMillis));
        out.setMemoryAvailableBytes(Math.max(0L, totalMemBytes - requestedMemBytes));
        out.setSchedulableNodes(schedulableNodeNames.size());
        return out;
    }

    private static boolean isUnschedulable(Node node) {
        if (node.getMetadata() == null || node.getMetadata().getName() == null) {
            return true;
        }
        return node.getSpec() != null && Boolean.TRUE.equals(node.getSpec().getUnschedulable());
    }

    private static boolean consumesScheduledCapacity(Pod pod, Set<String> schedulableNodes) {
        if (pod.getSpec() == null || pod.getSpec().getNodeName() == null) {
            return false;
        }
        if (!schedulableNodes.contains(pod.getSpec().getNodeName())) {
            return false;
        }
        String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : null;
        return phase == null || !"Succeeded".equals(phase) && !"Failed".equals(phase);
    }

    private static long sumPodRequestsCpuMillis(Pod pod) {
        long total = 0L;
        for (var container : pod.getSpec().getContainers()) {
            if (container.getResources() == null || container.getResources().getRequests() == null) {
                continue;
            }
            Quantity cpu = container.getResources().getRequests().get(Labels.RESOURCE_KEY_CPU);
            if (cpu != null) {
                total += QuantityToMillicores.toMillicores(cpu);
            }
        }
        return total;
    }

    private static long sumPodRequestsMemoryBytes(Pod pod) {
        long total = 0L;
        for (var container : pod.getSpec().getContainers()) {
            if (container.getResources() == null || container.getResources().getRequests() == null) {
                continue;
            }
            Quantity mem = container.getResources().getRequests().get(Labels.RESOURCE_KEY_MEMORY);
            if (mem != null) {
                total += QuantityToBytes.toBytes(mem);
            }
        }
        return total;
    }
}

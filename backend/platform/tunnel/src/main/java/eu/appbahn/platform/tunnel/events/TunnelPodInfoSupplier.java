package eu.appbahn.platform.tunnel.events;

import eu.appbahn.platform.api.resource.PodsResponse;
import eu.appbahn.platform.api.tunnel.ListPods;
import eu.appbahn.platform.api.tunnel.ListPodsResult;
import eu.appbahn.platform.api.tunnel.ListPodsResultEntry;
import eu.appbahn.platform.resource.service.PodInfoSupplier;
import eu.appbahn.platform.tunnel.command.CommandResponseAwaiter;
import eu.appbahn.platform.tunnel.command.CommandTypes;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * Tunnel-backed {@link PodInfoSupplier}: enqueues a {@link ListPods} command, blocks for
 * the operator's ack, and maps the {@link ListPodsResult} payload onto the public
 * {@link PodsResponse} shape returned to API callers.
 */
@Service
public class TunnelPodInfoSupplier implements PodInfoSupplier {

    /**
     * Operator typically replies in well under one second (fabric8 list + optional
     * metrics-server query). Five seconds is room for one slow round-trip without
     * letting an unhealthy cluster wedge the API thread for the full pending-command
     * sweeper window.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final CommandResponseAwaiter awaiter;

    public TunnelPodInfoSupplier(CommandResponseAwaiter awaiter) {
        this.awaiter = awaiter;
    }

    @Override
    public PodsResponse fetch(String clusterName, String namespace, String resourceSlug) {
        var command = new ListPods();
        command.setNamespace(namespace);
        command.setResourceSlug(resourceSlug);

        ListPodsResult result =
                awaiter.enqueueAndAwait(clusterName, CommandTypes.LIST_PODS, command, ListPodsResult.class, TIMEOUT);

        var response = new PodsResponse();
        for (ListPodsResultEntry p : result.getPods()) {
            var pod = new PodsResponse.PodInfo();
            pod.setName(p.getName());
            pod.setStatus(p.getStatus());
            pod.setNode(p.getNode());
            pod.setAgeSeconds(p.getAgeSeconds());
            if (p.getCpuUsedMillicores() != null || p.getCpuLimitMillicores() != null) {
                var cpu = new PodsResponse.PodCpuUsage();
                cpu.setUsedMillicores(p.getCpuUsedMillicores());
                cpu.setLimitMillicores(p.getCpuLimitMillicores());
                pod.setCpu(cpu);
            }
            if (p.getMemoryUsedBytes() != null || p.getMemoryLimitBytes() != null) {
                var mem = new PodsResponse.PodMemoryUsage();
                mem.setUsedBytes(p.getMemoryUsedBytes());
                mem.setLimitBytes(p.getMemoryLimitBytes());
                pod.setMemory(mem);
            }
            response.getPods().add(pod);
        }
        return response;
    }
}

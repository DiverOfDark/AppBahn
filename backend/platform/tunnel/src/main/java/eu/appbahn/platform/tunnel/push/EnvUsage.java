package eu.appbahn.platform.tunnel.push;

import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceConfig;
import io.fabric8.kubernetes.api.model.Quantity;

/**
 * Per-environment resource usage totals the admission snapshot ships. Aggregates across
 * all resources in the env, with replica multipliers applied, so the operator's webhook
 * can enforce quotas without ever reading from resource_cache.
 *
 * <p>CPU is expressed in millicores (1 core = 1000 millicores) to match the proto wire
 * type; memory in MiB; storage in GiB. Storage is currently always 0 — the CR spec has
 * no storage field yet, so there is nothing to sum (the webhook treats the dimension as
 * fail-open regardless).
 */
record EnvUsage(int resources, int cpuMillicores, int memoryMb, int storageGb, int replicas) {

    static EnvUsage empty() {
        return new EnvUsage(0, 0, 0, 0, 0);
    }

    EnvUsage add(int cpuMillicores, int memoryMb, int storageGb, int replicas) {
        return new EnvUsage(
                this.resources + 1,
                this.cpuMillicores + cpuMillicores,
                this.memoryMb + memoryMb,
                this.storageGb + storageGb,
                this.replicas + replicas);
    }

    static EnvUsage fromConfig(EnvUsage acc, ResourceConfig config) {
        if (config == null || config.getHosting() == null) {
            return acc.add(0, 0, 0, Labels.DEFAULT_REPLICAS);
        }
        ResourceConfig.HostingConfig hosting = config.getHosting();
        int replicas = hosting.getEffectiveReplicasForQuota() != null
                ? hosting.getEffectiveReplicasForQuota()
                : Labels.DEFAULT_REPLICAS;
        int cpuMillicores = cpuMillicores(hosting.getCpu()) * replicas;
        int memoryMb = memoryMb(hosting.getMemory()) * replicas;
        return acc.add(cpuMillicores, memoryMb, 0, replicas);
    }

    static int cpuMillicores(Quantity cpu) {
        if (cpu == null) {
            return 0;
        }
        return (int) Math.round(cpu.getNumericalAmount().doubleValue() * 1000.0);
    }

    static int memoryMb(Quantity memory) {
        if (memory == null) {
            return 0;
        }
        return (int) (Quantity.getAmountInBytes(memory).longValue() / (1024L * 1024L));
    }
}

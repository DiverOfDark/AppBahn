package eu.appbahn.operator.webhook;

import eu.appbahn.operator.tunnel.client.model.EnvironmentEntry;
import eu.appbahn.operator.tunnel.client.model.ProjectEntry;
import eu.appbahn.operator.tunnel.client.model.QuotaDimensions;
import eu.appbahn.operator.tunnel.client.model.WorkspaceEntry;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceConfig;
import io.fabric8.kubernetes.api.model.Quantity;
import java.util.List;
import java.util.Optional;

/**
 * Computes per-dimension quota decisions at env/project/workspace scopes for the admission
 * webhook. Fail-open semantics on every dimension: a limit of {@code 0} (or any dimension
 * whose "incoming" value cannot be derived from the CR) is skipped. The platform-side REST
 * path ({@code QuotaService}) is still authoritative — admission exists to catch
 * kubectl-direct applies that bypass the platform API.
 */
final class QuotaEnforcement {

    private QuotaEnforcement() {}

    /**
     * Derived resource cost of the CR under admission. {@code resources} is always 1 (the new
     * CR itself); other dimensions are parsed from {@code hosting.cpu / hosting.memory} and
     * multiplied by the effective replica count (maxReplicas if set, else minReplicas, else
     * {@link Labels#DEFAULT_REPLICAS}). Storage stays 0 — no CR-side source yet.
     */
    static QuotaDimensions incomingFromConfig(ResourceConfig config) {
        var d = new QuotaDimensions();
        d.setResources(1);
        if (config == null || config.getHosting() == null) {
            d.setReplicas(Labels.DEFAULT_REPLICAS);
            return d;
        }
        ResourceConfig.HostingConfig hosting = config.getHosting();
        int replicas = hosting.getEffectiveReplicasForQuota() != null
                ? hosting.getEffectiveReplicasForQuota()
                : Labels.DEFAULT_REPLICAS;
        d.setCpuMillicores(parseCpuMillicores(hosting.getCpu()) * replicas);
        d.setMemoryMb(parseMemoryMb(hosting.getMemory()) * replicas);
        d.setReplicas(replicas);
        return d;
    }

    /** Reason to send back to kube-apiserver when a dimension trips. */
    record Denial(String scope, String scopeSlug, String dimension, String unit, long total, long limit) {
        String message() {
            return scope + " " + scopeSlug + " " + dimension + " quota exceeded (" + format(total, unit) + "/"
                    + format(limit, unit) + ")";
        }

        private static String format(long value, String unit) {
            return unit.isEmpty() ? Long.toString(value) : value + unit;
        }
    }

    /**
     * Check all enforced dimensions at env scope. The env-level current usage already excludes
     * the CR under admission (it isn't in resource_cache yet), so we add {@code incoming} once.
     */
    static Optional<Denial> checkEnv(EnvironmentEntry env, QuotaDimensions incoming) {
        return firstExceeded(
                "environment",
                env.getSlug(),
                env.getCurrent() == null ? new QuotaDimensions() : env.getCurrent(),
                incoming,
                env.getLimits() == null ? new QuotaDimensions() : env.getLimits());
    }

    /**
     * Check all enforced dimensions at project scope by summing per-env current usage for envs
     * whose {@code projectSlug} matches. Storage + replicas are not enforced (storage has no
     * source on the CR spec yet; replicas is not a first-class Quota dimension on the platform).
     */
    static Optional<Denial> checkProject(
            ProjectEntry project, List<EnvironmentEntry> envsInProject, QuotaDimensions incoming) {
        return firstExceeded(
                "project",
                project.getSlug(),
                sumCurrent(envsInProject),
                incoming,
                project.getLimits() == null ? new QuotaDimensions() : project.getLimits());
    }

    static Optional<Denial> checkWorkspace(
            WorkspaceEntry workspace, List<EnvironmentEntry> envsInWorkspace, QuotaDimensions incoming) {
        return firstExceeded(
                "workspace",
                workspace.getSlug(),
                sumCurrent(envsInWorkspace),
                incoming,
                workspace.getLimits() == null ? new QuotaDimensions() : workspace.getLimits());
    }

    private static Optional<Denial> firstExceeded(
            String scope, String slug, QuotaDimensions current, QuotaDimensions incoming, QuotaDimensions limits) {
        // storage + replicas are intentionally omitted: no CR-side source / no Quota field.
        Optional<Denial> hit = check(
                scope,
                slug,
                "resources",
                "",
                orZero(current.getResources()),
                orZero(incoming.getResources()),
                orZero(limits.getResources()));
        if (hit.isPresent()) {
            return hit;
        }
        hit = check(
                scope,
                slug,
                "cpu",
                "m",
                orZero(current.getCpuMillicores()),
                orZero(incoming.getCpuMillicores()),
                orZero(limits.getCpuMillicores()));
        if (hit.isPresent()) {
            return hit;
        }
        return check(
                scope,
                slug,
                "memory",
                "MiB",
                orZero(current.getMemoryMb()),
                orZero(incoming.getMemoryMb()),
                orZero(limits.getMemoryMb()));
    }

    private static Optional<Denial> check(
            String scope, String slug, String dimension, String unit, long current, long incoming, long limit) {
        if (limit <= 0) {
            return Optional.empty();
        }
        long total = current + incoming;
        if (total <= limit) {
            return Optional.empty();
        }
        return Optional.of(new Denial(scope, slug, dimension, unit, total, limit));
    }

    private static QuotaDimensions sumCurrent(List<EnvironmentEntry> envs) {
        long resources = 0;
        long cpu = 0;
        long memory = 0;
        long storage = 0;
        long replicas = 0;
        for (var e : envs) {
            QuotaDimensions c = e.getCurrent();
            if (c == null) continue;
            resources += orZero(c.getResources());
            cpu += orZero(c.getCpuMillicores());
            memory += orZero(c.getMemoryMb());
            storage += orZero(c.getStorageGb());
            replicas += orZero(c.getReplicas());
        }
        var d = new QuotaDimensions();
        d.setResources((int) Math.min(Integer.MAX_VALUE, resources));
        d.setCpuMillicores((int) Math.min(Integer.MAX_VALUE, cpu));
        d.setMemoryMb((int) Math.min(Integer.MAX_VALUE, memory));
        d.setStorageGb((int) Math.min(Integer.MAX_VALUE, storage));
        d.setReplicas((int) Math.min(Integer.MAX_VALUE, replicas));
        return d;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static int parseCpuMillicores(Quantity cpu) {
        if (cpu == null) {
            return 0;
        }
        return (int) Math.round(cpu.getNumericalAmount().doubleValue() * 1000.0);
    }

    private static int parseMemoryMb(Quantity memory) {
        if (memory == null) {
            return 0;
        }
        return (int) (Quantity.getAmountInBytes(memory).longValue() / (1024L * 1024L));
    }
}

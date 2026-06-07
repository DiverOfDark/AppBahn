package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.resource.LogResponse;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.workspace.service.NamespaceService;
import eu.appbahn.shared.model.MemberRole;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolves the cluster + namespace for a Resource slug, applies the line-count default, and
 * delegates to the {@link LogsSupplier} (tunnel-backed) for the matched lines. Permission gate:
 * VIEWER on the Resource's environment — same level as listing pods or reading metrics.
 *
 * <p>The log provider lives entirely on the operator (it runs the LogsQL in-cluster). The platform
 * always issues the tunnel query; graceful degradation flows back from the operator, which returns
 * {@code available=false} (mapped to a "not available" message) when it has no provider configured.
 */
@Service
public class LogsService {

    public static final String NOT_AVAILABLE = "Logs not available — no log provider configured";

    /** Returned line count when {@code lines} is omitted. */
    static final int DEFAULT_LINES = 200;

    /** Cap on the number of returned lines, regardless of the requested value. */
    static final int MAX_LINES = 5000;

    private final ResourcePermissionHelper resourcePermissionHelper;
    private final NamespaceService namespaceService;
    private final LogsSupplier supplier;

    public LogsService(
            ResourcePermissionHelper resourcePermissionHelper,
            NamespaceService namespaceService,
            LogsSupplier supplier) {
        this.resourcePermissionHelper = resourcePermissionHelper;
        this.namespaceService = namespaceService;
        this.supplier = supplier;
    }

    public LogResponse query(
            String slug,
            String container,
            String pod,
            UUID deploymentId,
            Integer lines,
            OffsetDateTime since,
            AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.VIEWER);

        var env = resolved.env();
        String namespace = namespaceService.computeNamespace(env.getSlug());
        long sinceEpochSeconds = since != null ? since.toInstant().getEpochSecond() : 0L;
        int limit = resolveLimit(lines);

        return supplier.fetch(
                env.getTargetCluster(),
                namespace,
                slug,
                pod,
                container,
                deploymentId != null ? deploymentId.toString() : null,
                sinceEpochSeconds,
                limit);
    }

    /** An explicit positive {@code lines} is honored up to {@value #MAX_LINES}; otherwise the default. */
    static int resolveLimit(Integer lines) {
        if (lines == null || lines <= 0) {
            return DEFAULT_LINES;
        }
        return Math.min(lines, MAX_LINES);
    }
}

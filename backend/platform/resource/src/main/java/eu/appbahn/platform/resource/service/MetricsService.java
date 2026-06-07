package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.resource.MetricsResponse;
import eu.appbahn.platform.api.tunnel.MetricKind;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.workspace.service.NamespaceService;
import eu.appbahn.shared.model.MemberRole;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Resolves the cluster + namespace for a Resource slug, applies the time-range / step defaults,
 * and delegates to the {@link MetricsSupplier} (tunnel-backed) for the per-pod series. Permission
 * gate: VIEWER on the Resource's environment — same level as listing pods or reading metadata.
 *
 * <p>The metrics provider lives entirely on the operator (it runs the PromQL in-cluster). The
 * platform always issues the tunnel query; graceful degradation flows back from the operator,
 * which returns {@code available=false} (mapped to the {@link #NOT_AVAILABLE} message) when it has
 * no provider configured.
 */
@Service
public class MetricsService {

    public static final String NOT_AVAILABLE = "Metrics not available — no metrics provider configured";

    /** Default look-back when {@code start} is omitted. */
    static final Duration DEFAULT_RANGE = Duration.ofHours(1);

    /** Floor on auto-calculated resolution. */
    static final int MIN_STEP_SECONDS = 60;

    /** Cap the number of returned data points; drives the auto-step divisor. */
    static final int MAX_POINTS = 200;

    private static final Pattern RELATIVE = Pattern.compile("^-(\\d+)([smhd])$");

    private final ResourcePermissionHelper resourcePermissionHelper;
    private final NamespaceService namespaceService;
    private final MetricsSupplier supplier;

    public MetricsService(
            ResourcePermissionHelper resourcePermissionHelper,
            NamespaceService namespaceService,
            MetricsSupplier supplier) {
        this.resourcePermissionHelper = resourcePermissionHelper;
        this.namespaceService = namespaceService;
        this.supplier = supplier;
    }

    public MetricsResponse query(
            String slug, MetricKind kind, String start, String end, Integer step, String pod, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(slug, ctx, MemberRole.VIEWER);

        Instant now = Instant.now();
        Instant endInstant = parseEnd(end, now);
        Instant startInstant = parseStart(start, endInstant);
        int resolvedStep = resolveStep(step, startInstant, endInstant);

        var window = new MetricsResponse();
        window.setStart(startInstant.atOffset(ZoneOffset.UTC));
        window.setEnd(endInstant.atOffset(ZoneOffset.UTC));
        window.setStep(resolvedStep);

        var env = resolved.env();
        String namespace = namespaceService.computeNamespace(env.getSlug());
        MetricsResponse fetched = supplier.fetch(
                env.getTargetCluster(),
                namespace,
                slug,
                kind,
                startInstant.getEpochSecond(),
                endInstant.getEpochSecond(),
                resolvedStep,
                pod);
        fetched.setStart(window.getStart());
        fetched.setEnd(window.getEnd());
        fetched.setStep(resolvedStep);
        return fetched;
    }

    /**
     * Auto-step is {@code max(range / MAX_POINTS, MIN_STEP_SECONDS)} so a chart never carries more
     * than {@value #MAX_POINTS} points and never resolves finer than {@value #MIN_STEP_SECONDS}s.
     * An explicit {@code step} is honored as long as it is positive.
     */
    static int resolveStep(Integer explicit, Instant start, Instant end) {
        if (explicit != null && explicit > 0) {
            return explicit;
        }
        long rangeSeconds = Math.max(0L, Duration.between(start, end).getSeconds());
        long auto = rangeSeconds / MAX_POINTS;
        return (int) Math.max(auto, MIN_STEP_SECONDS);
    }

    private static Instant parseEnd(String end, Instant now) {
        if (end == null || end.isBlank() || "now".equalsIgnoreCase(end.trim())) {
            return now;
        }
        Instant relative = parseRelative(end, now);
        return relative != null ? relative : parseAbsolute(end);
    }

    private static Instant parseStart(String start, Instant end) {
        if (start == null || start.isBlank()) {
            return end.minus(DEFAULT_RANGE);
        }
        Instant relative = parseRelative(start, end);
        return relative != null ? relative : parseAbsolute(start);
    }

    /** Relative offsets ({@code -1h}, {@code -24h}, {@code -7d}) are measured back from {@code anchor}. */
    private static Instant parseRelative(String value, Instant anchor) {
        Matcher m = RELATIVE.matcher(value.trim());
        if (!m.matches()) {
            return null;
        }
        long amount = Long.parseLong(m.group(1));
        Duration d =
                switch (m.group(2)) {
                    case "s" -> Duration.ofSeconds(amount);
                    case "m" -> Duration.ofMinutes(amount);
                    case "h" -> Duration.ofHours(amount);
                    case "d" -> Duration.ofDays(amount);
                    default -> Duration.ZERO;
                };
        return anchor.minus(d);
    }

    private static Instant parseAbsolute(String value) {
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid time value: '" + value + "' (expected ISO 8601 or relative like -1h/-24h/-7d)");
        }
    }
}

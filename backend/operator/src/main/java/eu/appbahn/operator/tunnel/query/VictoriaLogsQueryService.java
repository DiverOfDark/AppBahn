package eu.appbahn.operator.tunnel.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.operator.tunnel.client.model.LogsResult;
import eu.appbahn.operator.tunnel.client.model.LogsResultLine;
import eu.appbahn.shared.Labels;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Runs LogsQL queries against the in-cluster Victoria Logs for the pods backing a Resource. Pods
 * are resolved by the {@code appbahn.eu/resource} label and folded into a
 * {@code kubernetes.pod_name:~"a|b"} regex filter, because Victoria Logs indexes the cluster's
 * standard Kubernetes log labels — not arbitrary CRD labels.
 *
 * <p>Graceful degradation: when no Victoria Logs endpoint is configured, {@link #query} returns a
 * {@link LogsResult} with {@code available=false}; the platform maps that to the "not available"
 * message. A query error against a configured endpoint surfaces as an empty-but-available result.
 */
@Service
public class VictoriaLogsQueryService {

    static final String NO_PROVIDER = "no log provider configured";
    static final String NO_ENDPOINT = "no Victoria Logs URL configured";

    private static final Logger log = LoggerFactory.getLogger(VictoriaLogsQueryService.class);

    private final KubernetesClient kubernetesClient;
    private final LogsProviderConfig config;
    private final ObjectMapper mapper;
    private final RestClient restClient;

    public VictoriaLogsQueryService(KubernetesClient kubernetesClient, LogsProviderConfig config, ObjectMapper mapper) {
        this.kubernetesClient = kubernetesClient;
        this.config = config;
        this.mapper = mapper;
        RestClient.Builder builder = RestClient.builder();
        if (config.configured()) {
            builder.baseUrl(config.endpoint());
        }
        this.restClient = builder.build();
    }

    public LogsResult query(
            String namespace,
            String resourceSlug,
            String podFilter,
            String containerFilter,
            long sinceEpochSeconds,
            int limit) {
        var result = new LogsResult();
        if (!config.configured()) {
            result.setAvailable(false);
            result.setMessage(config.type() == LogsProviderType.NONE ? NO_PROVIDER : NO_ENDPOINT);
            return result;
        }
        result.setAvailable(true);

        List<String> podNames = resolvePodNames(namespace, resourceSlug, podFilter);
        if (podNames.isEmpty()) {
            return result;
        }

        String logsql = buildQuery(namespace, podNames, containerFilter, sinceEpochSeconds);
        String body = runQuery(logsql, limit);
        if (body == null || body.isBlank()) {
            return result;
        }
        for (String line : body.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            try {
                VictoriaLogsLine parsed = mapper.readValue(line, VictoriaLogsLine.class);
                var entry = new LogsResultLine();
                entry.setTimestamp(parseTimestamp(parsed.getTime()));
                entry.setMessage(parsed.getMessage());
                entry.setPod(parsed.getPod());
                entry.setContainer(parsed.getContainer());
                result.addLinesItem(entry);
            } catch (Exception e) {
                log.debug("Skipping unparseable Victoria Logs line: {}", e.getMessage());
            }
        }
        return result;
    }

    private List<String> resolvePodNames(String namespace, String resourceSlug, String podFilter) {
        if (podFilter != null && !podFilter.isBlank()) {
            return List.of(podFilter);
        }
        return kubernetesClient
                .pods()
                .inNamespace(namespace)
                .withLabel(Labels.RESOURCE_KEY, resourceSlug)
                .list()
                .getItems()
                .stream()
                .map(Pod::getMetadata)
                .filter(m -> m != null && m.getName() != null)
                .map(io.fabric8.kubernetes.api.model.ObjectMeta::getName)
                .toList();
    }

    /**
     * LogsQL scoped to the namespace + a {@code kubernetes.pod_name} regex, with an optional
     * container filter and a lower {@code _time} bound. The cluster's logs agent writes the
     * standard Kubernetes stream fields, so we filter on those rather than CRD labels. A
     * deployment-id filter narrows to the pods of that release upstream (the platform passes the
     * resolved pod names) — Victoria Logs has no per-deployment stream field of its own.
     */
    static String buildQuery(String namespace, List<String> podNames, String containerFilter, long sinceEpochSeconds) {
        String podRegex = podNames.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        StringBuilder q = new StringBuilder();
        q.append("kubernetes.namespace_name:").append(quote(namespace));
        q.append(" kubernetes.pod_name:~").append(quote(podRegex));
        if (containerFilter != null && !containerFilter.isBlank()) {
            q.append(" kubernetes.container_name:").append(quote(containerFilter));
        }
        if (sinceEpochSeconds > 0) {
            q.append(" _time:>=").append(Instant.ofEpochSecond(sinceEpochSeconds));
        }
        return q.toString();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String runQuery(String query, int limit) {
        try {
            return restClient
                    .get()
                    .uri(uri -> uri.path("/select/logsql/query")
                            .queryParam("query", query)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn(
                    "Victoria Logs query failed against {} ({}); returning empty result",
                    config.endpoint(),
                    e.getMessage());
            return null;
        }
    }

    /** Victoria Logs emits RFC-3339 {@code _time}; map to a fractional Unix epoch second. */
    private static double parseTimestamp(String time) {
        if (time == null || time.isBlank()) {
            return 0.0;
        }
        try {
            Instant instant = Instant.parse(time);
            return instant.getEpochSecond() + instant.getNano() / 1_000_000_000.0;
        } catch (DateTimeParseException e) {
            return 0.0;
        }
    }
}

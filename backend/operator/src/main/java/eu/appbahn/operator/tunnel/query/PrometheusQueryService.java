package eu.appbahn.operator.tunnel.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.operator.tunnel.client.model.MetricsResult;
import eu.appbahn.operator.tunnel.client.model.MetricsResultSample;
import eu.appbahn.operator.tunnel.client.model.MetricsResultSeries;
import eu.appbahn.operator.tunnel.client.model.QueryMetrics;
import eu.appbahn.shared.Labels;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Runs PromQL range queries against the in-cluster Prometheus for the pods backing a Resource.
 * Pods are resolved by the {@code appbahn.eu/resource} label and folded into a {@code pod=~"a|b"}
 * regex selector, because cAdvisor's {@code container_*} series carry only {@code namespace} and
 * {@code pod} labels — not arbitrary Kubernetes labels.
 *
 * <p>Graceful degradation: when no usable metrics provider is configured, {@link #query} returns a
 * {@link MetricsResult} with {@code available=false} and a distinct message (no provider vs. no
 * Prometheus URL); the platform surfaces that message. A query error against a configured endpoint
 * surfaces as an empty-but-available result.
 */
@Service
public class PrometheusQueryService {

    private static final Logger log = LoggerFactory.getLogger(PrometheusQueryService.class);

    private final KubernetesClient kubernetesClient;
    private final MetricsProviderConfig config;
    private final RestClient restClient;

    public PrometheusQueryService(
            KubernetesClient kubernetesClient, MetricsProviderConfig config, ObjectMapper objectMapper) {
        this.kubernetesClient = kubernetesClient;
        this.config = config;
        // Pin the Jackson 2 converter (bound to the project ObjectMapper) ahead of the framework
        // default: with Jackson 3 on the classpath, RestClient otherwise picks a Jackson 3 converter
        // that ignores this codebase's Jackson 2 annotations — including the custom Sample
        // deserializer that maps Prometheus's [timestamp, value] tuples.
        RestClient.Builder builder = RestClient.builder()
                .messageConverters(
                        converters -> converters.add(0, new MappingJackson2HttpMessageConverter(objectMapper)));
        if (config.configured()) {
            builder.baseUrl(config.endpoint());
        }
        this.restClient = builder.build();
    }

    public MetricsResult query(
            String namespace,
            String resourceSlug,
            QueryMetrics.KindEnum kind,
            long startEpochSeconds,
            long endEpochSeconds,
            int stepSeconds,
            String podFilter) {
        var result = new MetricsResult();
        if (!config.configured()) {
            result.setAvailable(false);
            result.setMessage(config.unavailableReason());
            return result;
        }
        result.setAvailable(true);

        List<String> podNames = resolvePodNames(namespace, resourceSlug, podFilter);
        if (podNames.isEmpty()) {
            return result;
        }

        String promql = buildQuery(kind, namespace, podNames);
        PrometheusResponse response = runRangeQuery(promql, startEpochSeconds, endEpochSeconds, stepSeconds);
        if (response == null || response.getData() == null) {
            return result;
        }
        for (PrometheusResponse.Result r : response.getData().getResult()) {
            var series = new MetricsResultSeries();
            series.setPod(r.getMetric().get("pod"));
            List<MetricsResultSample> samples = new ArrayList<>();
            for (PrometheusResponse.Sample point : r.getValues()) {
                Double value = parseValue(point.getValue());
                if (value == null) {
                    continue;
                }
                var sample = new MetricsResultSample();
                sample.setTimestamp(point.getTimestamp());
                sample.setValue(value);
                samples.add(sample);
            }
            series.setValues(samples);
            result.addSeriesItem(series);
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
     * PromQL per metric kind, scoped to {@code namespace} and a {@code pod=~"…"} regex. CPU is
     * already in cores (rate of cpu-seconds); RAM is working-set bytes; network is byte/sec rate.
     */
    static String buildQuery(QueryMetrics.KindEnum kind, String namespace, List<String> podNames) {
        String podRegex = podNames.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        String selector = "namespace=\"" + namespace + "\",pod=~\"" + podRegex + "\"";
        return switch (kind) {
            case CPU -> "sum by (pod) (rate(container_cpu_usage_seconds_total{" + selector + "}[2m30s]))";
            case RAM -> "sum by (pod) (container_memory_working_set_bytes{" + selector + "})";
            case NETWORK_INBOUND -> "sum by (pod) (rate(container_network_receive_bytes_total{" + selector + "}[2m]))";
            case NETWORK_OUTBOUND ->
                "sum by (pod) (rate(container_network_transmit_bytes_total{" + selector + "}[2m]))";
        };
    }

    private PrometheusResponse runRangeQuery(String query, long start, long end, int step) {
        try {
            return restClient
                    .get()
                    .uri(uri -> uri.path("/api/v1/query_range")
                            // PromQL carries literal {} (label selectors). Pass it as a URI
                            // variable so the builder encodes the braces as a value rather than
                            // treating them as URI-template placeholders to expand.
                            .queryParam("query", "{query}")
                            .queryParam("start", start)
                            .queryParam("end", end)
                            .queryParam("step", step)
                            .build(query))
                    .retrieve()
                    .body(PrometheusResponse.class);
        } catch (Exception e) {
            log.warn(
                    "Prometheus query_range failed against {} ({}); returning empty series",
                    config.endpoint(),
                    e.getMessage());
            return null;
        }
    }

    private static Double parseValue(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

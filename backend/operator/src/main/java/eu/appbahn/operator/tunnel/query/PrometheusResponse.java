package eu.appbahn.operator.tunnel.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Typed view of a Prometheus {@code /api/v1/query_range} response (matrix result type). Only the
 * fields the operator consumes are mapped; unknown fields are ignored so a Prometheus minor-version
 * bump doesn't break parsing.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrometheusResponse {

    private String status;
    private QueryData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QueryData {
        private String resultType;
        private List<Result> result = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private Map<String, String> metric = Map.of();
        private List<Sample> values = new ArrayList<>();
    }

    /**
     * One matrix sample. Prometheus serializes each as a 2-element JSON array
     * {@code [unixSeconds (number), value (string)]}; {@link SampleDeserializer} maps that array
     * onto the typed pair.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonDeserialize(using = SampleDeserializer.class)
    public static class Sample {
        private double timestamp;
        private String value;
    }

    static final class SampleDeserializer extends JsonDeserializer<Sample> {
        @Override
        public Sample deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.readValueAsTree();
            if (!node.isArray() || node.size() < 2) {
                throw new IOException("Expected a [timestamp, value] Prometheus sample, got: " + node);
            }
            return new Sample(node.get(0).asDouble(), node.get(1).asText());
        }
    }
}

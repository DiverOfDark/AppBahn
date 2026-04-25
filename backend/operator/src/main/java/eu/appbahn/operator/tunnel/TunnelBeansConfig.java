package eu.appbahn.operator.tunnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.operator.tunnel.client.ApiClient;
import eu.appbahn.operator.tunnel.client.api.TunnelApi;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the openapi-generator-produced {@link TunnelApi} client over an {@link ApiClient}
 * configured for this operator's platform URL. The client shares the Spring-managed
 * {@link ObjectMapper} so polymorphic DTOs ({@code OperatorEvent} subtypes, source configs)
 * round-trip through the same Jackson configuration as the SSE reader.
 */
@Configuration
public class TunnelBeansConfig {

    @Bean
    public TunnelApi generatedTunnelApi(OperatorTunnelConfig config, ObjectMapper objectMapper) {
        // Generated client's spec-derived baseUri is "/api/tunnel/v1" (from servers[0].url);
        // prefix the operator's platform URL and it becomes the real absolute endpoint.
        ApiClient apiClient =
                new ApiClient(HttpClient.newBuilder(), objectMapper, config.platformBaseUrl() + "/api/tunnel/v1");
        apiClient.setReadTimeout(Duration.ofSeconds(30));
        return new TunnelApi(apiClient);
    }
}

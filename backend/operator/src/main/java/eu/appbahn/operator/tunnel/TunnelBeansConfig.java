package eu.appbahn.operator.tunnel;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared beans for the tunnel stack. Spring Boot provides an {@link
 * com.fasterxml.jackson.databind.ObjectMapper} bean by default; we add a reusable
 * {@link HttpClient} since the JDK HTTP client isn't auto-configured.
 */
@Configuration
public class TunnelBeansConfig {

    @Bean
    public HttpClient tunnelHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }
}

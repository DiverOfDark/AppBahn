package eu.appbahn.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.appbahn.shared.jackson.QuantityJackson3Module;
import eu.appbahn.shared.jackson.QuantityJacksonModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Provides Jackson configuration for both the legacy {@code com.fasterxml.jackson} stack (Jackson
 * 2) and Spring Boot 4's default Jackson 3 stack ({@code tools.jackson.*}).
 *
 * <p>Jackson 2 is explicitly instantiated here for code that injects the legacy
 * {@link ObjectMapper}. Spring Boot's Jackson auto-configuration does NOT touch this explicit
 * {@code @Primary} bean, so any module that should be active has to be registered directly
 * (hence {@link QuantityJacksonModule}).
 *
 * <p>Jackson 3 is what Spring MVC's HTTP message converters use for request/response bodies on
 * Spring Boot 4. We register {@link QuantityJackson3Module} as a {@code JacksonModule} bean so
 * Spring Boot picks it up and applies it to the auto-configured Jackson 3 JsonMapper — otherwise
 * platform API responses serialize fabric8 {@code Quantity} as an {@code {amount,format}} object
 * instead of the {@code "100m"} string shape the public-api.yaml contract promises.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new QuantityJacksonModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public tools.jackson.databind.JacksonModule quantityJackson3Module() {
        return new QuantityJackson3Module();
    }
}

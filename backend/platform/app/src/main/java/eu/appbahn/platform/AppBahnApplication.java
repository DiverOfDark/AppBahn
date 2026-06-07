package eu.appbahn.platform;

import eu.appbahn.platform.resource.service.DeploymentRetentionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableRetry
@EnableConfigurationProperties({DeploymentRetentionProperties.class})
public class AppBahnApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppBahnApplication.class, args);
    }
}

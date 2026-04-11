package eu.appbahn.operator;

import eu.appbahn.operator.reconciler.OperatorConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(OperatorConfig.class)
public class OperatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OperatorApplication.class, args);
    }
}

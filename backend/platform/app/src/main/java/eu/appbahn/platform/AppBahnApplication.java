package eu.appbahn.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AppBahnApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppBahnApplication.class, args);
    }
}
